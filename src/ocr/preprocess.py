from __future__ import annotations

import json
import math
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any


SUPPORTED_PROFILES = ("none", "basic", "document_scan", "poster_safe")
PROFILE_ALIASES = {
    "raw": "none",
    "grayscale": "document_scan",
    "light_denoise": "document_scan",
}


@dataclass
class PreprocessResult:
    input_path: str
    output_path: str
    preprocess_profile: str
    operations: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    original_size: list[int] = field(default_factory=list)
    output_size: list[int] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any]:
        return asdict(self)


def normalize_profile(profile: str) -> str:
    normalized = PROFILE_ALIASES.get(profile, profile)
    if normalized not in SUPPORTED_PROFILES:
        raise ValueError(f"Unsupported preprocess profile: {profile}. Supported: {', '.join(SUPPORTED_PROFILES)}")
    return normalized


def preprocess_image(image_path: Path, output_dir: Path, profile: str = "none") -> PreprocessResult:
    """Prepare an image for OCR and return reproducibility metadata.

    Profiles are intentionally conservative. Thresholding is not applied by
    default because it can destroy colored poster text and faint layout cues.
    """
    profile = normalize_profile(profile)
    image_path = image_path.resolve()
    if profile == "none":
        size = _image_size_or_empty(image_path)
        return PreprocessResult(
            input_path=image_path.as_posix(),
            output_path=image_path.as_posix(),
            preprocess_profile=profile,
            operations=["use_original_image"],
            original_size=size,
            output_size=size,
        )

    try:
        from PIL import Image, ImageEnhance, ImageFilter, ImageOps
    except ImportError as exc:
        raise RuntimeError("Pillow is required for OCR preprocessing profiles other than 'none'.") from exc

    output_dir.mkdir(parents=True, exist_ok=True)
    operations: list[str] = []
    warnings: list[str] = []

    with Image.open(image_path) as source:
        original_size = [source.width, source.height]
        image = ImageOps.exif_transpose(source)
        if image.size != source.size:
            operations.append("orientation_correction_exif")
        else:
            operations.append("orientation_checked_no_rotation")

        if profile == "basic":
            image = _upscale_if_small(image, min_long_side=1600, operations=operations)

        elif profile == "poster_safe":
            image = _upscale_if_small(image, min_long_side=1800, operations=operations)
            image = ImageEnhance.Sharpness(image).enhance(1.08)
            operations.append("mild_sharpen_color_preserved")
            warnings.append("thresholding_skipped_to_preserve_colored_poster_text")

        elif profile == "document_scan":
            image = _upscale_if_small(image, min_long_side=1800, operations=operations)
            image = image.convert("L")
            operations.append("grayscale")
            image = ImageOps.autocontrast(image)
            operations.append("autocontrast")
            image = image.filter(ImageFilter.MedianFilter(size=3))
            operations.append("median_denoise_size_3")
            image, deskew_warning = _deskew_if_available(image)
            if deskew_warning:
                warnings.append(deskew_warning)
            else:
                operations.append("deskew_cv2")
            warnings.append("hard_thresholding_skipped_to_avoid_text_loss")

        output_path = output_dir / f"{image_path.stem}.{profile}{image_path.suffix}"
        image.save(output_path)

    result = PreprocessResult(
        input_path=image_path.as_posix(),
        output_path=output_path.resolve().as_posix(),
        preprocess_profile=profile,
        operations=operations,
        warnings=warnings,
        original_size=original_size,
        output_size=_image_size_or_empty(output_path),
    )
    result.metadata["output_bytes"] = output_path.stat().st_size
    return result


def prepare_image(image_path: Path, output_dir: Path, profile: str = "none") -> Path:
    """Backward-compatible helper that returns only the prepared image path."""
    return Path(preprocess_image(image_path, output_dir, profile).output_path)


def write_preprocess_log(path: Path, rows: list[PreprocessResult]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row.to_json(), ensure_ascii=False, sort_keys=True) + "\n")


def _image_size_or_empty(image_path: Path) -> list[int]:
    try:
        from PIL import Image

        with Image.open(image_path) as image:
            return [image.width, image.height]
    except Exception:
        return []


def _upscale_if_small(image: Any, min_long_side: int, operations: list[str]) -> Any:
    long_side = max(image.width, image.height)
    if long_side >= min_long_side:
        operations.append(f"resize_skipped_long_side_{long_side}")
        return image
    scale = min_long_side / max(long_side, 1)
    target_size = [max(1, math.ceil(image.width * scale)), max(1, math.ceil(image.height * scale))]
    resample = _resampling_lanczos()
    operations.append(f"upscale_long_side_to_{min_long_side}")
    return image.resize(tuple(target_size), resample=resample)


def _resampling_lanczos() -> Any:
    from PIL import Image

    return getattr(getattr(Image, "Resampling", Image), "LANCZOS")


def _deskew_if_available(image: Any) -> tuple[Any, str | None]:
    try:
        import cv2
        import numpy as np
    except ImportError:
        return image, "deskew_skipped_cv2_not_installed"

    array = np.array(image)
    inverted = cv2.bitwise_not(array)
    coords = np.column_stack(np.where(inverted > 0))
    if coords.size == 0:
        return image, "deskew_skipped_no_foreground_pixels"
    angle = cv2.minAreaRect(coords)[-1]
    if angle < -45:
        angle = -(90 + angle)
    else:
        angle = -angle
    if abs(angle) < 0.3:
        return image, "deskew_skipped_angle_too_small"
    height, width = array.shape[:2]
    center = (width // 2, height // 2)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
    rotated = cv2.warpAffine(array, matrix, (width, height), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    from PIL import Image

    return Image.fromarray(rotated), None
