from __future__ import annotations

from pathlib import Path
from typing import Any

from .base import OCRBase, OCRBlock, OCRResult


class EasyOCREngine(OCRBase):
    name = "easyocr"

    def __init__(self, languages: list[str] | None = None, gpu: bool = False) -> None:
        self.languages = languages or ["ko", "en"]
        self.gpu = gpu
        self._reader: Any | None = None

    def _load(self) -> Any:
        if self._reader is not None:
            return self._reader
        try:
            import easyocr
        except ImportError as exc:
            raise RuntimeError("EasyOCR is not installed. Install it before running this engine.") from exc
        self._reader = easyocr.Reader(self.languages, gpu=self.gpu)
        return self._reader

    def engine_version(self) -> str:
        try:
            import easyocr

            return getattr(easyocr, "__version__", "unknown")
        except ImportError:
            return "not-installed"

    def recognize(self, image_path: Path, notice_id: str, preprocess_profile: str = "raw") -> OCRResult:
        reader = self._load()
        raw_result = reader.readtext(str(image_path), detail=1, paragraph=False)
        blocks: list[OCRBlock] = []
        for bbox_raw, text, confidence in raw_result:
            xs = [point[0] for point in bbox_raw]
            ys = [point[1] for point in bbox_raw]
            blocks.append(
                OCRBlock(
                    text=str(text).strip(),
                    bbox=[float(min(xs)), float(min(ys)), float(max(xs)), float(max(ys))],
                    confidence=float(confidence),
                    block_type="text",
                )
            )
        plain_text = "\n".join(block.text for block in blocks if block.text)
        return OCRResult(
            notice_id=notice_id,
            file_path=str(image_path.as_posix()),
            ocr_engine=self.name,
            engine_version=self.engine_version(),
            preprocess_profile=preprocess_profile,
            plain_text=plain_text,
            blocks=blocks,
        )

