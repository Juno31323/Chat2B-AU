from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .base import read_sample_jsonl
from .preprocess import SUPPORTED_PROFILES, preprocess_image, write_preprocess_log


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SAMPLE_PATH = ROOT / "data" / "processed" / "ocr_outputs" / "sample_images.jsonl"
DEFAULT_OUTPUT_DIR = ROOT / "data" / "processed" / "ocr_outputs" / "preprocessed"
DEFAULT_LOG_PATH = ROOT / "data" / "processed" / "ocr_outputs" / "preprocess_results.jsonl"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run OCR image preprocessing only, without OCR.")
    parser.add_argument("--samples", type=Path, default=DEFAULT_SAMPLE_PATH)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--log", type=Path, default=DEFAULT_LOG_PATH)
    parser.add_argument("--profile", default="all", choices=[*SUPPORTED_PROFILES, "all", "raw", "grayscale", "light_denoise"])
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    samples = read_sample_jsonl(args.samples)
    if args.limit > 0:
        samples = samples[: args.limit]
    if not samples:
        print(f"No OCR samples found: {args.samples}", file=sys.stderr)
        return 2

    profiles = list(SUPPORTED_PROFILES) if args.profile == "all" else [args.profile]
    rows = []
    failures: list[dict[str, str]] = []
    for sample in samples:
        image_path = (ROOT / sample.file_path).resolve() if not Path(sample.file_path).is_absolute() else Path(sample.file_path)
        for profile in profiles:
            try:
                rows.append(preprocess_image(image_path, args.output_dir, profile))
            except Exception as exc:
                failures.append({"notice_id": sample.notice_id, "file_path": sample.file_path, "profile": profile, "error": str(exc)})

    log_path = args.log.resolve()
    write_preprocess_log(log_path, rows)
    failure_path = log_path.with_suffix(".failures.jsonl")
    with failure_path.open("w", encoding="utf-8", newline="\n") as handle:
        for failure in failures:
            handle.write(json.dumps(failure, ensure_ascii=False, sort_keys=True) + "\n")

    print(
        json.dumps(
            {
                "samples": len(samples),
                "profiles": profiles,
                "succeeded": len(rows),
                "failed": len(failures),
                "log": display_path(log_path),
                "failures": display_path(failure_path),
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
