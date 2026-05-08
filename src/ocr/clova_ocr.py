from __future__ import annotations

import base64
import json
import os
import time
import uuid
from pathlib import Path
from typing import Any

from .base import OCRBase, OCRBlock, OCRResult


class ClovaOCREngine(OCRBase):
    name = "clova-ocr"

    def __init__(self, invoke_url: str | None = None, secret_key: str | None = None) -> None:
        self.invoke_url = invoke_url or os.getenv("CLOVA_OCR_INVOKE_URL", "")
        self.secret_key = secret_key or os.getenv("CLOVA_OCR_SECRET_KEY", "")

    def engine_version(self) -> str:
        return os.getenv("CLOVA_OCR_VERSION", "clova-ocr-api")

    def recognize(self, image_path: Path, notice_id: str, preprocess_profile: str = "raw") -> OCRResult:
        if not self.invoke_url or not self.secret_key:
            raise RuntimeError("CLOVA_OCR_INVOKE_URL and CLOVA_OCR_SECRET_KEY must be set in .env or environment.")
        try:
            import requests
        except ImportError as exc:
            raise RuntimeError("requests is required for CLOVA OCR.") from exc

        image_bytes = image_path.read_bytes()
        payload = {
            "version": "V2",
            "requestId": str(uuid.uuid4()),
            "timestamp": int(time.time() * 1000),
            "images": [
                {
                    "format": image_path.suffix.lstrip(".").lower() or "png",
                    "name": image_path.stem,
                    "data": base64.b64encode(image_bytes).decode("ascii"),
                }
            ],
        }
        response = requests.post(
            self.invoke_url,
            headers={"X-OCR-SECRET": self.secret_key, "Content-Type": "application/json; charset=UTF-8"},
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            timeout=60,
        )
        response.raise_for_status()
        result: dict[str, Any] = response.json()
        blocks: list[OCRBlock] = []
        for image in result.get("images", []):
            for field in image.get("fields", []):
                vertices = field.get("boundingPoly", {}).get("vertices", [])
                xs = [point.get("x", 0) for point in vertices] or [0]
                ys = [point.get("y", 0) for point in vertices] or [0]
                blocks.append(
                    OCRBlock(
                        text=str(field.get("inferText", "")).strip(),
                        bbox=[float(min(xs)), float(min(ys)), float(max(xs)), float(max(ys))],
                        confidence=float(field.get("inferConfidence", 0.0) or 0.0),
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
            metadata={"request_id": payload["requestId"]},
        )

