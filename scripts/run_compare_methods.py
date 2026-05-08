#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import glob
import hashlib
import json
import math
import re
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIGS = [
    ROOT / "configs" / "rag_text_only.yaml",
    ROOT / "configs" / "rag_ocr_plain.yaml",
    ROOT / "configs" / "rag_ocr_layout.yaml",
]
DEFAULT_QUESTIONS = ROOT / "experiments" / "questions.jsonl"
DEFAULT_RUNS_DIR = ROOT / "experiments" / "runs"
TOKEN_PATTERN = re.compile(r"[0-9A-Za-z가-힣]+")


@dataclass
class Question:
    question_id: str
    question: str
    category: str = ""
    important: bool = False
    expected_source_keywords: list[str] = field(default_factory=list)


@dataclass
class MethodConfig:
    path: Path
    method: str
    dataset_path: str
    index_namespace: str
    generation_provider: str
    generation_model: str
    generation_important_model: str
    prompt_version: str
    temperature: float
    max_output_tokens: int
    chunk_size: int
    chunk_overlap: int
    embedding_model: str
    embedding_dim: int
    bm25_top_k: int
    dense_top_k: int
    final_top_k: int
    fusion_method: str
    rrf_k: int
    refusal_min_dense_score: float
    refusal_min_token_overlap: int
    refusal_min_token_coverage: float


@dataclass
class Document:
    document_id: str
    title: str
    path: str
    url: str | None
    posted_at: str | None
    content_type: str
    metadata: dict[str, Any]
    text: str


@dataclass
class Chunk:
    chunk_id: str
    document_id: str
    title: str
    path: str
    url: str | None
    posted_at: str | None
    content_type: str
    section: str
    chunk_index: int
    text: str
    metadata: dict[str, Any]


@dataclass
class RankedChunk:
    chunk: Chunk
    bm25_score: float | None = None
    dense_score: float | None = None
    hybrid_score: float = 0.0
    bm25_rank: int | None = None
    dense_rank: int | None = None
    hybrid_rank: int | None = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare Text-only, OCR-RAG, and Layout-aware OCR-RAG with identical questions.")
    parser.add_argument("--questions", type=Path, default=DEFAULT_QUESTIONS)
    parser.add_argument("--config", action="append", type=Path, default=[], help="Config path. Defaults to all three RAG configs.")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--jsonl", type=Path, default=None)
    parser.add_argument("--csv", type=Path, default=None)
    parser.add_argument("--limit", type=int, default=0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    configs = [load_method_config(path.resolve()) for path in (args.config or DEFAULT_CONFIGS)]
    questions = read_questions(args.questions.resolve())
    if args.limit > 0:
        questions = questions[: args.limit]
    if not questions:
        raise SystemExit(f"No questions found: {args.questions}")

    run_dir = args.output_dir.resolve() if args.output_dir else create_run_dir()
    run_dir.mkdir(parents=True, exist_ok=True)
    jsonl_path = args.jsonl.resolve() if args.jsonl else run_dir / "results.jsonl"
    csv_path = args.csv.resolve() if args.csv else run_dir / "results.csv"
    log_path = run_dir / "run_log.json"

    rows: list[dict[str, Any]] = []
    method_summaries: list[dict[str, Any]] = []
    started_at = datetime.now(timezone.utc)

    for config in configs:
        method_start = time.perf_counter()
        documents = load_documents(config)
        chunks = chunk_documents(documents, config)
        index = build_index(chunks, config)
        method_summaries.append(
            {
                "method": config.method,
                "config": relative(config.path),
                "dataset_path": config.dataset_path,
                "document_count": len(documents),
                "chunk_count": len(chunks),
                "generation_provider": config.generation_provider,
                "generation_model": config.generation_model,
                "generation_important_model": config.generation_important_model,
                "prompt_version": config.prompt_version,
                "temperature": config.temperature,
                "max_output_tokens": config.max_output_tokens,
                "final_top_k": config.final_top_k,
            }
        )
        for question in questions:
            query_start = time.perf_counter()
            ranked = retrieve(question.question, chunks, index, config)
            refusal, refusal_reason = should_refuse(question.question, ranked, config)
            answer = compose_answer(question.question, ranked, refusal, config)
            latency_ms = round((time.perf_counter() - query_start) * 1000, 3)
            rows.append(build_result_row(question, config, ranked, answer, refusal, refusal_reason, latency_ms))
        method_summaries[-1]["method_latency_ms"] = round((time.perf_counter() - method_start) * 1000, 3)

    write_jsonl(jsonl_path, rows)
    write_csv(csv_path, rows)
    write_json(
        log_path,
        {
            "started_at": started_at.isoformat(),
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "questions": relative(args.questions.resolve()),
            "result_jsonl": relative(jsonl_path),
            "result_csv": relative(csv_path),
            "methods": method_summaries,
            "notes": "Offline comparison runner using hashed dense retrieval, BM25, and RRF with the same config values.",
        },
    )
    print(json.dumps({"run_dir": relative(run_dir), "results": relative(jsonl_path), "csv": relative(csv_path), "rows": len(rows)}, ensure_ascii=False, indent=2))
    return 0


def load_method_config(path: Path) -> MethodConfig:
    parsed = parse_simple_yaml(path)
    retrieval = parsed.get("retrieval", {})
    refusal = parsed.get("refusal_threshold", {})
    generation = parsed.get("generation", {})
    return MethodConfig(
        path=path,
        method=str(parsed.get("condition", path.stem)),
        dataset_path=str(parsed.get("dataset_path", "")),
        index_namespace=str(parsed.get("index_namespace", "")),
        generation_provider=str(generation.get("provider", parsed.get("generation_provider", "mock"))),
        generation_model=str(generation.get("model", parsed.get("generation_model", "demo-extractive"))),
        generation_important_model=str(generation.get("important_model", parsed.get("generation_important_model", ""))),
        prompt_version=str(generation.get("prompt_version", parsed.get("prompt_version", "grounded_qa_v1"))),
        temperature=float(generation.get("temperature", parsed.get("temperature", 0.0))),
        max_output_tokens=int(generation.get("max_output_tokens", parsed.get("max_tokens", 512))),
        chunk_size=int(parsed.get("chunk_size", 700)),
        chunk_overlap=int(parsed.get("chunk_overlap", 0)),
        embedding_model=str(parsed.get("embedding_model", "hashed-256")),
        embedding_dim=int(parsed.get("embedding_dim", 256)),
        bm25_top_k=int(retrieval.get("bm25_top_k", 50)),
        dense_top_k=int(retrieval.get("dense_top_k", 50)),
        final_top_k=int(retrieval.get("final_top_k", 5)),
        fusion_method=str(retrieval.get("fusion_method", "rrf")),
        rrf_k=int(retrieval.get("rrf_k", 60)),
        refusal_min_dense_score=float(refusal.get("min_dense_score", 0.22)),
        refusal_min_token_overlap=int(refusal.get("min_token_overlap", 1)),
        refusal_min_token_coverage=float(refusal.get("min_token_coverage", 0.30)),
    )


def parse_simple_yaml(path: Path) -> dict[str, Any]:
    result: dict[str, Any] = {}
    current_section: str | None = None
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        if not raw_line.startswith(" ") and ":" in raw_line:
            key, raw_value = raw_line.split(":", 1)
            key = key.strip()
            raw_value = raw_value.strip()
            if raw_value:
                result[key] = parse_scalar(raw_value)
                current_section = None
            else:
                result[key] = {}
                current_section = key
            continue
        if current_section and raw_line.startswith("  ") and ":" in raw_line:
            key, raw_value = raw_line.strip().split(":", 1)
            section = result.setdefault(current_section, {})
            if isinstance(section, dict):
                section[key.strip()] = parse_scalar(raw_value.strip())
    return result


def parse_scalar(value: str) -> Any:
    value = value.strip().strip('"').strip("'")
    if value.lower() in {"true", "false"}:
        return value.lower() == "true"
    try:
        if "." in value:
            return float(value)
        return int(value)
    except ValueError:
        return value


def read_questions(path: Path) -> list[Question]:
    questions: list[Question] = []
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        payload = json.loads(line)
        question_id = str(payload.get("question_id") or f"q{index:03d}")
        questions.append(
            Question(
                question_id=question_id,
                question=str(payload["question"]),
                category=str(payload.get("category", "")),
                important=bool(payload.get("important", False)),
                expected_source_keywords=list(payload.get("expected_source_keywords", [])),
            )
        )
    return questions


def load_documents(config: MethodConfig) -> list[Document]:
    paths = sorted(Path(match).resolve() for match in glob.glob(str(ROOT / config.dataset_path), recursive=True))
    documents: list[Document] = []
    for path in paths:
        if path.suffix.lower() not in {".md", ".txt"}:
            continue
        raw = path.read_text(encoding="utf-8")
        metadata, body = split_frontmatter(raw)
        title = str(metadata.get("title") or first_heading(body) or path.stem.replace("_", " "))
        documents.append(
            Document(
                document_id=path.stem,
                title=title,
                path=relative(path),
                url=str(metadata.get("url") or "") or None,
                posted_at=str(metadata.get("posted_at") or "") or None,
                content_type=str(metadata.get("content_type") or infer_content_type(path)),
                metadata=metadata,
                text=body,
            )
        )
    return documents


def split_frontmatter(raw: str) -> tuple[dict[str, Any], str]:
    if not raw.startswith("---\n"):
        return {}, raw
    end = raw.find("\n---", 4)
    if end < 0:
        return {}, raw
    frontmatter = raw[4:end]
    body = raw[end + 4 :]
    metadata: dict[str, Any] = {}
    for line in frontmatter.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        metadata[key.strip()] = parse_scalar(value.strip())
    return metadata, body


def first_heading(text: str) -> str | None:
    for line in text.splitlines():
        if line.startswith("#"):
            return re.sub(r"^#+\s*", "", line).strip()
    return None


def infer_content_type(path: Path) -> str:
    normalized = path.as_posix()
    if "/ocr_plain/" in normalized:
        return "image_ocr_plain"
    if "/ocr_layout/" in normalized and "/layout_chunks/" in normalized:
        return "image_ocr_layout_chunk"
    return "text"


def chunk_documents(documents: list[Document], config: MethodConfig) -> list[Chunk]:
    chunks: list[Chunk] = []
    for document in documents:
        sections = split_sections(document.text)
        chunk_index = 0
        for section_name, section_text in sections:
            for content in chunk_section(section_text, config.chunk_size):
                chunks.append(
                    Chunk(
                        chunk_id=f"{document.document_id}:{chunk_index}",
                        document_id=document.document_id,
                        title=document.title,
                        path=document.path,
                        url=document.url,
                        posted_at=document.posted_at,
                        content_type=document.content_type,
                        section=section_name,
                        chunk_index=chunk_index,
                        text=content,
                        metadata=document.metadata,
                    )
                )
                chunk_index += 1
    return chunks


def split_sections(text: str) -> list[tuple[str, str]]:
    sections: list[tuple[str, str]] = []
    current_title = "Document"
    buffer: list[str] = []
    for line in text.replace("\r", "").split("\n"):
        if re.match(r"^#{1,6}\s+.+", line):
            append_section(sections, current_title, buffer)
            current_title = re.sub(r"^#{1,6}\s+", "", line).strip()
            continue
        buffer.append(line)
    append_section(sections, current_title, buffer)
    return sections


def append_section(sections: list[tuple[str, str]], title: str, buffer: list[str]) -> None:
    content = normalize_text("\n".join(buffer))
    if content:
        sections.append((title, content))
    buffer.clear()


def chunk_section(text: str, chunk_size: int) -> list[str]:
    paragraphs = [normalize_text(part) for part in re.split(r"\n\s*\n", text) if normalize_text(part)]
    if not paragraphs:
        paragraphs = [text]
    chunks: list[str] = []
    current = ""
    hard_split_size = max(200, min(chunk_size - 80, chunk_size))
    for paragraph in paragraphs:
        if len(paragraph) > chunk_size:
            if current:
                chunks.append(current.strip())
                current = ""
            chunks.extend(paragraph[i : i + hard_split_size].strip() for i in range(0, len(paragraph), hard_split_size))
            continue
        if current and len(current) + len(paragraph) + 2 > chunk_size and len(current) >= 180:
            chunks.append(current.strip())
            current = ""
        current = paragraph if not current else current + "\n\n" + paragraph
    if current.strip():
        chunks.append(current.strip())
    return chunks


def normalize_text(value: str) -> str:
    normalized = value.replace("\r", "")
    normalized = re.sub(r"[ \t]+", " ", normalized)
    normalized = re.sub(r"\n{3,}", "\n\n", normalized)
    return normalized.strip()


def build_index(chunks: list[Chunk], config: MethodConfig) -> dict[str, Any]:
    tokenized = [tokenize(chunk.text) for chunk in chunks]
    df: dict[str, int] = {}
    for tokens in tokenized:
        for token in set(tokens):
            df[token] = df.get(token, 0) + 1
    avgdl = sum(len(tokens) for tokens in tokenized) / max(len(tokenized), 1)
    embeddings = [hashed_embedding(chunk.text, config.embedding_dim) for chunk in chunks]
    return {"tokenized": tokenized, "df": df, "avgdl": avgdl, "embeddings": embeddings}


def retrieve(question: str, chunks: list[Chunk], index: dict[str, Any], config: MethodConfig) -> list[RankedChunk]:
    query_tokens = tokenize(question)
    query_embedding = hashed_embedding(question, config.embedding_dim)
    bm25_ranked = rank_bm25(chunks, index, query_tokens, config.bm25_top_k)
    dense_ranked = rank_dense(chunks, index, query_embedding, config.dense_top_k)

    merged: dict[str, RankedChunk] = {}
    for rank, (chunk, score) in enumerate(bm25_ranked, start=1):
        item = merged.setdefault(chunk.chunk_id, RankedChunk(chunk=chunk))
        item.bm25_score = score
        item.bm25_rank = rank
    for rank, (chunk, score) in enumerate(dense_ranked, start=1):
        item = merged.setdefault(chunk.chunk_id, RankedChunk(chunk=chunk))
        item.dense_score = score
        item.dense_rank = rank
    for item in merged.values():
        score = 0.0
        if item.bm25_rank is not None:
            score += 1.0 / (config.rrf_k + item.bm25_rank)
        if item.dense_rank is not None:
            score += 1.0 / (config.rrf_k + item.dense_rank)
        item.hybrid_score = score
    ranked = sorted(merged.values(), key=lambda item: item.hybrid_score, reverse=True)[: config.final_top_k]
    for index_, item in enumerate(ranked, start=1):
        item.hybrid_rank = index_
    return ranked


def rank_bm25(chunks: list[Chunk], index: dict[str, Any], query_tokens: list[str], limit: int) -> list[tuple[Chunk, float]]:
    n_docs = len(chunks)
    avgdl = index["avgdl"] or 1.0
    df = index["df"]
    tokenized = index["tokenized"]
    k1 = 1.5
    b = 0.75
    scores: list[tuple[Chunk, float]] = []
    for chunk, tokens in zip(chunks, tokenized):
        if not tokens:
            continue
        tf: dict[str, int] = {}
        for token in tokens:
            tf[token] = tf.get(token, 0) + 1
        score = 0.0
        dl = len(tokens)
        for token in query_tokens:
            if token not in tf:
                continue
            idf = math.log(1 + (n_docs - df.get(token, 0) + 0.5) / (df.get(token, 0) + 0.5))
            freq = tf[token]
            score += idf * (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * dl / avgdl))
        if score > 0:
            scores.append((chunk, score))
    return sorted(scores, key=lambda item: item[1], reverse=True)[:limit]


def rank_dense(chunks: list[Chunk], index: dict[str, Any], query_embedding: list[float], limit: int) -> list[tuple[Chunk, float]]:
    scores = [(chunk, cosine(query_embedding, embedding)) for chunk, embedding in zip(chunks, index["embeddings"])]
    return sorted(scores, key=lambda item: item[1], reverse=True)[:limit]


def should_refuse(question: str, ranked: list[RankedChunk], config: MethodConfig) -> tuple[bool, str]:
    if not ranked:
        return True, "NO_RETRIEVAL_RESULT"
    query_tokens = set(tokenize(question))
    top_tokens = set(tokenize(" ".join(item.chunk.text for item in ranked[: min(3, len(ranked))])))
    overlap = len(query_tokens & top_tokens)
    coverage = overlap / max(len(query_tokens), 1)
    best_dense = max((item.dense_score or 0.0) for item in ranked)
    if overlap < config.refusal_min_token_overlap:
        return True, "LOW_TOKEN_OVERLAP"
    if coverage < config.refusal_min_token_coverage and best_dense < config.refusal_min_dense_score:
        return True, "LOW_EVIDENCE_COVERAGE"
    return False, ""


def compose_answer(question: str, ranked: list[RankedChunk], refusal: bool, config: MethodConfig) -> str:
    if refusal:
        return "제공된 공지에서 확인되지 않습니다."
    snippets: list[str] = []
    query_tokens = set(tokenize(question))
    for item in ranked:
        snippet = best_snippet(item.chunk.text, query_tokens)
        if snippet and snippet not in snippets:
            snippets.append(snippet)
        if sum(len(part) for part in snippets) >= config.max_output_tokens:
            break
    if not snippets:
        snippets = [ranked[0].chunk.text[: config.max_output_tokens]]
    return "\n\n".join(snippets)[: config.max_output_tokens]


def best_snippet(text: str, query_tokens: set[str]) -> str:
    sentences = re.split(r"(?<=[.!?。！？])\s+|\n+", text)
    best = ""
    best_score = -1
    for sentence in sentences:
        normalized = normalize_text(sentence)
        if not normalized:
            continue
        score = len(set(tokenize(normalized)) & query_tokens)
        if score > best_score:
            best = normalized
            best_score = score
    return best[:500]


def build_result_row(
    question: Question,
    config: MethodConfig,
    ranked: list[RankedChunk],
    answer: str,
    refusal: bool,
    refusal_reason: str,
    latency_ms: float,
) -> dict[str, Any]:
    retrieved_chunks = [chunk_to_payload(item) for item in ranked]
    source_titles = unique(item.chunk.title for item in ranked)
    source_urls = unique(item.chunk.url or "" for item in ranked if item.chunk.url)
    posted_at = unique(item.chunk.posted_at or "" for item in ranked if item.chunk.posted_at)
    return {
        "question_id": question.question_id,
        "question": question.question,
        "category": question.category,
        "important": question.important,
        "method": config.method,
        "answer": answer,
        "retrieved_chunks": retrieved_chunks,
        "source_titles": source_titles,
        "source_urls": source_urls,
        "posted_at": posted_at,
        "retrieval_scores": [
            {
                "chunk_id": item.chunk.chunk_id,
                "bm25_score": item.bm25_score,
                "dense_score": item.dense_score,
                "hybrid_score": item.hybrid_score,
                "bm25_rank": item.bm25_rank,
                "dense_rank": item.dense_rank,
                "hybrid_rank": item.hybrid_rank,
            }
            for item in ranked
        ],
        "latency_ms": latency_ms,
        "refusal": refusal,
        "refusal_reason": refusal_reason,
        "generation_provider": config.generation_provider,
        "generation_model": effective_generation_model(question, config),
        "base_generation_model": config.generation_model,
        "important_generation_model": config.generation_important_model,
        "model_version": effective_generation_model(question, config),
        "temperature": config.temperature,
        "max_output_tokens": config.max_output_tokens,
        "prompt_version": config.prompt_version,
        "run_date": datetime.now(timezone.utc).isoformat(),
        "input_tokens": None,
        "output_tokens": None,
        "total_tokens": None,
        "estimated_cost_usd": None,
        "top_k": config.final_top_k,
    }


def chunk_to_payload(item: RankedChunk) -> dict[str, Any]:
    metadata_keys = [
        "notice_id",
        "source_image_path",
        "ocr_engine",
        "preprocess_profile",
        "block_type",
        "bbox",
        "page",
        "reading_order",
        "confidence",
        "extracted_dates",
        "extracted_contacts",
    ]
    layout_metadata = {key: item.chunk.metadata.get(key) for key in metadata_keys if key in item.chunk.metadata}
    return {
        "chunk_id": item.chunk.chunk_id,
        "document_id": item.chunk.document_id,
        "title": item.chunk.title,
        "url": item.chunk.url,
        "posted_at": item.chunk.posted_at,
        "content_type": item.chunk.content_type,
        "section": item.chunk.section,
        "preview": item.chunk.text[:300],
        "layout_metadata": layout_metadata,
    }


def tokenize(value: str) -> list[str]:
    return [match.group(0).lower() for match in TOKEN_PATTERN.finditer(value)]


def hashed_embedding(value: str, dimensions: int) -> list[float]:
    vector = [0.0] * dimensions
    for token in tokenize(value):
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % dimensions
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[index] += sign
    norm = math.sqrt(sum(value_ * value_ for value_ in vector))
    if norm:
        vector = [value_ / norm for value_ in vector]
    return vector


def cosine(left: list[float], right: list[float]) -> float:
    if not left or not right:
        return 0.0
    return sum(a * b for a, b in zip(left, right))


def create_run_dir() -> Path:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return DEFAULT_RUNS_DIR / f"{stamp}_compare_methods"


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "question_id",
        "question",
        "category",
        "important",
        "method",
        "answer",
        "source_titles",
        "source_urls",
        "posted_at",
        "latency_ms",
        "refusal",
        "refusal_reason",
        "generation_provider",
        "generation_model",
        "base_generation_model",
        "important_generation_model",
        "model_version",
        "temperature",
        "max_output_tokens",
        "prompt_version",
        "run_date",
        "input_tokens",
        "output_tokens",
        "total_tokens",
        "estimated_cost_usd",
        "top_k",
    ]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: json.dumps(row[key], ensure_ascii=False) if isinstance(row.get(key), list) else row.get(key, "") for key in fieldnames})


def unique(items: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        if item and item not in seen:
            result.append(item)
            seen.add(item)
    return result


def effective_generation_model(question: Question, config: MethodConfig) -> str:
    if question.important and config.generation_important_model:
        return config.generation_important_model
    return config.generation_model


def relative(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.as_posix()


if __name__ == "__main__":
    raise SystemExit(main())
