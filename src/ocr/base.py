from __future__ import annotations

import json
from abc import ABC, abstractmethod
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Literal


BlockType = Literal["text", "title", "table", "unknown"]


@dataclass
class OCRBlock:
    text: str
    bbox: list[float]
    confidence: float
    page: int = 1
    block_type: BlockType = "unknown"


@dataclass
class OCRResult:
    notice_id: str
    file_path: str
    ocr_engine: str
    engine_version: str
    preprocess_profile: str
    plain_text: str
    blocks: list[OCRBlock] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class OCRSample:
    notice_id: str
    file_path: str
    sample_type: str
    expected_features: list[str] = field(default_factory=list)
    note: str = ""


class OCRBase(ABC):
    name: str = "base"

    @abstractmethod
    def engine_version(self) -> str:
        """Return engine/library version, or 'unknown' if unavailable."""

    @abstractmethod
    def recognize(self, image_path: Path, notice_id: str, preprocess_profile: str = "raw") -> OCRResult:
        """Run OCR for one image and return a normalized result."""


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def read_sample_jsonl(path: Path) -> list[OCRSample]:
    samples: list[OCRSample] = []
    if not path.exists():
        return samples
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        samples.append(
            OCRSample(
                notice_id=payload["notice_id"],
                file_path=payload["file_path"],
                sample_type=payload.get("sample_type", "unknown"),
                expected_features=list(payload.get("expected_features", [])),
                note=payload.get("note", ""),
            )
        )
    return samples

