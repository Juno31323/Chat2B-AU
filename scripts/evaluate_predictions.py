#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PREDICTIONS = ROOT / "experiments" / "runs" / "results.jsonl"
DEFAULT_OUTPUT = ROOT / "experiments" / "runs" / "metrics.csv"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compute notice-level retrieval metrics from compare-method prediction results.")
    parser.add_argument("--predictions", type=Path, required=True, help="JSONL file created by scripts/run_compare_methods.py")
    parser.add_argument("--output", type=Path, default=None, help="Metrics CSV output path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    prediction_path = args.predictions.resolve()
    output_path = args.output.resolve() if args.output else prediction_path.parent / "metrics.csv"
    rows = read_jsonl(prediction_path)
    if not rows:
        raise SystemExit(f"No prediction rows found: {prediction_path}")

    metric_rows: list[dict[str, Any]] = []
    for method, method_rows in group_by(rows, "method").items():
        metric_rows.append(compute_metrics(method, "all", method_rows))
        for subset, subset_rows in group_by(method_rows, "category").items():
            if subset:
                metric_rows.append(compute_metrics(method, subset, subset_rows))

    write_metrics(output_path, metric_rows)
    print(json.dumps({"metrics": relative(output_path), "rows": len(metric_rows)}, ensure_ascii=False, indent=2))
    return 0


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            if line.strip():
                rows.append(json.loads(line))
    return rows


def group_by(rows: list[dict[str, Any]], key: str) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        grouped[str(row.get(key) or "")].append(row)
    return dict(grouped)


def compute_metrics(method: str, subset: str, rows: list[dict[str, Any]]) -> dict[str, Any]:
    answerable_rows = [row for row in rows if row.get("gold_notice_id")]
    ood_rows = [row for row in rows if not row.get("gold_notice_id")]
    recall1 = mean(hit_at(row, 1) for row in answerable_rows)
    recall3 = mean(hit_at(row, 3) for row in answerable_rows)
    recall5 = mean(hit_at(row, 5) for row in answerable_rows)
    reciprocal_ranks = [reciprocal_rank(row) for row in answerable_rows]
    ndcg_scores = [ndcg_at_5(row) for row in answerable_rows]
    refusal_accuracy = mean(1.0 if bool(row.get("refusal")) else 0.0 for row in ood_rows)
    return {
        "method": method,
        "subset": subset,
        "recall_at_1": round_or_blank(recall1),
        "recall_at_3": round_or_blank(recall3),
        "recall_at_5": round_or_blank(recall5),
        "mrr": round_or_blank(mean(reciprocal_ranks)),
        "ndcg_at_5": round_or_blank(mean(ndcg_scores)),
        "answer_accuracy": "",
        "faithfulness": "",
        "answer_relevance": "",
        "source_accuracy": "",
        "hallucination_rate": "",
        "context_precision": "",
        "date_accuracy": "",
        "place_accuracy": "",
        "contact_accuracy": "",
        "target_accuracy": "",
        "table_qa_accuracy": "",
        "refusal_accuracy": round_or_blank(refusal_accuracy),
        "avg_latency_ms": round_or_blank(mean(float(row.get("latency_ms") or 0.0) for row in rows)),
        "note": "Retrieval metrics are notice_id-level. Generation/field metrics require human or LLM-as-judge labeling.",
    }


def notice_ids(row: dict[str, Any]) -> list[str]:
    value = row.get("retrieved_notice_ids") or []
    if isinstance(value, list):
        return [str(item) for item in value if item]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [str(item) for item in parsed if item]
        except json.JSONDecodeError:
            return [item.strip() for item in value.split("|") if item.strip()]
    return []


def hit_at(row: dict[str, Any], k: int) -> float:
    gold = str(row.get("gold_notice_id") or "")
    if not gold:
        return 0.0
    return 1.0 if gold in notice_ids(row)[:k] else 0.0


def reciprocal_rank(row: dict[str, Any]) -> float:
    gold = str(row.get("gold_notice_id") or "")
    if not gold:
        return 0.0
    for rank, notice_id in enumerate(notice_ids(row), start=1):
        if notice_id == gold:
            return 1.0 / rank
    return 0.0


def ndcg_at_5(row: dict[str, Any]) -> float:
    gold = str(row.get("gold_notice_id") or "")
    if not gold:
        return 0.0
    for rank, notice_id in enumerate(notice_ids(row)[:5], start=1):
        if notice_id == gold:
            return 1.0 / math.log2(rank + 1)
    return 0.0


def mean(values: Any) -> float | None:
    materialized = list(values)
    if not materialized:
        return None
    return sum(materialized) / len(materialized)


def round_or_blank(value: float | None) -> str:
    if value is None:
        return ""
    return f"{value:.6f}"


def write_metrics(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "method",
        "subset",
        "recall_at_1",
        "recall_at_3",
        "recall_at_5",
        "mrr",
        "ndcg_at_5",
        "answer_accuracy",
        "faithfulness",
        "answer_relevance",
        "source_accuracy",
        "hallucination_rate",
        "context_precision",
        "date_accuracy",
        "place_accuracy",
        "contact_accuracy",
        "target_accuracy",
        "table_qa_accuracy",
        "refusal_accuracy",
        "avg_latency_ms",
        "note",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def relative(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.as_posix()


if __name__ == "__main__":
    raise SystemExit(main())
