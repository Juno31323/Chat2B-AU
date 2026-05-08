from __future__ import annotations

from pathlib import Path
from typing import Any

from .base import OCRBase, OCRBlock, OCRResult


class PaddleOCREngine(OCRBase):
    name = "paddleocr"

    def __init__(self, lang: str = "korean", use_structure: bool = False) -> None:
        self.lang = lang
        self.use_structure = use_structure
        self._engine: Any | None = None

    def _load(self) -> Any:
        if self._engine is not None:
            return self._engine
        try:
            if self.use_structure:
                from paddleocr import PPStructure

                self._engine = PPStructure(lang=self.lang, show_log=False)
            else:
                from paddleocr import PaddleOCR

                self._engine = PaddleOCR(lang=self.lang, use_angle_cls=True, show_log=False)
        except ImportError as exc:
            raise RuntimeError("PaddleOCR is not installed. Install it before running this engine.") from exc
        return self._engine

    def engine_version(self) -> str:
        try:
            import paddleocr

            return getattr(paddleocr, "__version__", "unknown")
        except ImportError:
            return "not-installed"

    def recognize(self, image_path: Path, notice_id: str, preprocess_profile: str = "raw") -> OCRResult:
        engine = self._load()
        blocks: list[OCRBlock] = []

        if self.use_structure:
            raw_result = engine(str(image_path))
            for item in raw_result:
                bbox = item.get("bbox") or [0, 0, 0, 0]
                text = item.get("res", "")
                if isinstance(text, list):
                    text = " ".join(str(part.get("text", "")) for part in text if isinstance(part, dict))
                blocks.append(
                    OCRBlock(
                        text=str(text).strip(),
                        bbox=[float(v) for v in bbox],
                        confidence=float(item.get("confidence", 0.0) or 0.0),
                        block_type=str(item.get("type", "unknown")) if item.get("type") in {"text", "title", "table"} else "unknown",
                    )
                )
        else:
            raw_result = engine.ocr(str(image_path), cls=True)
            for page in raw_result or []:
                for line in page or []:
                    bbox_raw, text_raw = line
                    text, confidence = text_raw
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
            ocr_engine=self.name if not self.use_structure else "pp-structure",
            engine_version=self.engine_version(),
            preprocess_profile=preprocess_profile,
            plain_text=plain_text,
            blocks=blocks,
        )

