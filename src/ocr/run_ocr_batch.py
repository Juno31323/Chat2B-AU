from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from time import perf_counter

from .base import OCRResult, read_sample_jsonl, write_jsonl
from .clova_ocr import ClovaOCREngine
from .easyocr_engine import EasyOCREngine
from .paddle_ocr import PaddleOCREngine
from .preprocess import SUPPORTED_PROFILES, preprocess_image, write_preprocess_log


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SAMPLE_PATH = ROOT / "data" / "processed" / "ocr_outputs" / "sample_images.jsonl"
DEFAULT_OUTPUT_PATH = ROOT / "data" / "processed" / "ocr_outputs" / "ocr_results.jsonl"
DEFAULT_PREPROCESSED_DIR = ROOT / "data" / "processed" / "ocr_outputs" / "preprocessed"


def build_engine(name: str):
    if name == "paddle":
        return PaddleOCREngine(use_structure=False)
    if name == "pp-structure":
        return PaddleOCREngine(use_structure=True)
    if name == "clova":
        return ClovaOCREngine()
    if name == "easyocr":
        return EasyOCREngine()
    raise ValueError(f"Unsupported OCR engine: {name}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run OCR pilot batch for Ansan notice images.")
    parser.add_argument("--engine", choices=["paddle", "pp-structure", "clova", "easyocr"], required=True)
    parser.add_argument("--samples", type=Path, default=DEFAULT_SAMPLE_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_PATH)
    parser.add_argument("--preprocess-profile", default="none", choices=[*SUPPORTED_PROFILES, "all", "raw", "grayscale", "light_denoise"])
    parser.add_argument("--limit", type=int, default=0, help="Optional max number of samples. 0 means all.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    samples = read_sample_jsonl(args.samples)
    if args.limit > 0:
        samples = samples[: args.limit]
    if not samples:
        print(f"No OCR samples found: {args.samples}", file=sys.stderr)
        return 2

    engine = build_engine(args.engine)
    rows: list[dict[str, object]] = []
    failures: list[dict[str, object]] = []
    preprocess_rows = []
    profiles = list(SUPPORTED_PROFILES) if args.preprocess_profile == "all" else [args.preprocess_profile]

    for sample in samples:
        image_path = (ROOT / sample.file_path).resolve() if not Path(sample.file_path).is_absolute() else Path(sample.file_path)
        for profile in profiles:
            started = perf_counter()
            try:
                preprocess_result = preprocess_image(image_path, DEFAULT_PREPROCESSED_DIR, profile)
                preprocess_rows.append(preprocess_result)
                prepared_path = Path(preprocess_result.output_path)
                result: OCRResult = engine.recognize(prepared_path, sample.notice_id, preprocess_result.preprocess_profile)
                payload = result.to_json()
                payload["sample_type"] = sample.sample_type
                payload["expected_features"] = sample.expected_features
                payload["elapsed_seconds"] = round(perf_counter() - started, 4)
                payload["preprocess"] = preprocess_result.to_json()
                rows.append(payload)
            except Exception as exc:
                failures.append(
                    {
                        "notice_id": sample.notice_id,
                        "file_path": sample.file_path,
                        "ocr_engine": args.engine,
                        "preprocess_profile": profile,
                        "error": str(exc),
                    }
                )

    output_path = args.output.resolve()
    write_jsonl(output_path, rows)
    failure_path = output_path.with_suffix(".failures.jsonl")
    preprocess_log_path = output_path.with_suffix(".preprocess.jsonl")
    write_jsonl(failure_path, failures)
    write_preprocess_log(preprocess_log_path, preprocess_rows)
    print(
        json.dumps(
            {
                "engine": args.engine,
                "preprocess_profile": args.preprocess_profile,
                "samples": len(samples),
                "profile_runs": len(samples) * len(profiles),
                "succeeded": len(rows),
                "failed": len(failures),
                "output": display_path(output_path),
                "failures": display_path(failure_path),
                "preprocess_log": display_path(preprocess_log_path),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 1 if failures and not rows else 0


def display_path(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.as_posix()


if __name__ == "__main__":
    raise SystemExit(main())
