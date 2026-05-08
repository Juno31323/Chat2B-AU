#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
TEXT_DOCS_DIR = ROOT / "src" / "main" / "resources" / "admissions-docs"
DEFAULT_OCR_RESULTS = ROOT / "data" / "processed" / "ocr_outputs" / "ocr_results.jsonl"
DEFAULT_OUTPUT_DIR = ROOT / "data" / "processed" / "ocr_plain" / "corpus"
DEFAULT_INDEX_DIR = ROOT / "indices" / "ocr_plain"
DEFAULT_NOTICE_MANIFEST = ROOT / "data" / "raw" / "ansan_notices" / "manifest.jsonl"
CHUNK_SIZE = 700
CHUNK_OVERLAP = 0
EMBEDDING_MODEL = "hashed-256"
EMBEDDING_DIM = 256
TOKENIZER = "unicode-letter-number-v1"
BM25_TOKENIZER = "unicode-korean-v1"
INDEX_NAME = "ocr_plain"
INDEX_VERSION = "ocr_plain_v1"
CORPUS_PROFILE = "ocr_plain"


@dataclass
class CorpusDocument:
    document_id: str
    title: str
    source_path: str
    output_path: str
    content_type: str
    notice_id: str | None = None
    source_image_path: str | None = None
    ocr_engine: str | None = None
    preprocess_profile: str | None = None
    url: str | None = None
    posted_at: str | None = None
    source_hash: str | None = None
    chunk_count: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build OCR plain corpus without touching text_only index.")
    parser.add_argument("--text-docs", type=Path, default=TEXT_DOCS_DIR)
    parser.add_argument("--ocr-results", type=Path, default=DEFAULT_OCR_RESULTS)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--index-dir", type=Path, default=DEFAULT_INDEX_DIR)
    parser.add_argument("--notice-manifest", type=Path, default=DEFAULT_NOTICE_MANIFEST)
    parser.add_argument("--include-example", action="store_true", help="Allow ocr_results.example.jsonl for schema demos only.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = args.output_dir.resolve()
    index_dir = args.index_dir.resolve()
    reset_dir(output_dir)
    index_dir.mkdir(parents=True, exist_ok=True)

    documents: list[CorpusDocument] = []
    for path in sorted(args.text_docs.glob("*.md")):
        target = output_dir / path.name
        shutil.copy2(path, target)
        text = path.read_text(encoding="utf-8")
        documents.append(
            CorpusDocument(
                document_id=path.stem,
                title=path.stem.replace("_", " "),
                source_path=relative(path),
                output_path=relative(target),
                content_type="text",
                source_hash=sha256_text(text),
                chunk_count=count_chunks(text),
            )
        )

    notice_metadata = load_notice_metadata(args.notice_manifest.resolve())
    ocr_documents = load_ocr_documents(args.ocr_results.resolve(), args.include_example, notice_metadata)
    for ocr_doc in ocr_documents:
        target = output_dir / f"ocr_{safe_filename(ocr_doc['notice_id'])}_{safe_filename(ocr_doc['ocr_engine'])}_{safe_filename(ocr_doc['preprocess_profile'])}.md"
        content = render_ocr_markdown(ocr_doc)
        target.write_text(content, encoding="utf-8", newline="\n")
        documents.append(
            CorpusDocument(
                document_id=target.stem,
                title=ocr_doc["title"],
                source_path=ocr_doc["source_image_path"],
                output_path=relative(target),
                content_type="image_ocr_plain",
                notice_id=ocr_doc["notice_id"],
                source_image_path=ocr_doc["source_image_path"],
                ocr_engine=ocr_doc["ocr_engine"],
                preprocess_profile=ocr_doc["preprocess_profile"],
                url=ocr_doc.get("url"),
                posted_at=ocr_doc.get("posted_at"),
                source_hash=sha256_text(content),
                chunk_count=count_chunks(content),
            )
        )

    write_json(index_dir / "corpus_manifest.json", build_corpus_manifest(documents, args.ocr_results.resolve()))
    write_json(index_dir / "index_metadata.json", build_index_metadata(documents, args.ocr_results.resolve()))
    write_sample_questions(index_dir / "sample_questions.jsonl")
    write_sample_results_placeholder(index_dir / "sample_results.pending.jsonl", ocr_documents)
    write_readme(index_dir / "README.md")

    summary = {
        "condition": "ocr_plain",
        "documents": len(documents),
        "text_documents": sum(1 for doc in documents if doc.content_type == "text"),
        "ocr_documents": sum(1 for doc in documents if doc.content_type == "image_ocr_plain"),
        "chunks": sum(doc.chunk_count for doc in documents),
        "ocr_chunks": sum(doc.chunk_count for doc in documents if doc.content_type == "image_ocr_plain"),
        "corpus": relative(output_dir),
        "index_metadata": relative(index_dir / "index_metadata.json"),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def reset_dir(path: Path) -> None:
    resolved = path.resolve()
    if resolved.exists():
        if not resolved.is_dir() or not resolved.is_relative_to(ROOT):
            raise RuntimeError(f"Refusing to reset path outside workspace: {resolved}")
        shutil.rmtree(resolved)
    resolved.mkdir(parents=True, exist_ok=True)


def load_notice_metadata(path: Path) -> dict[str, dict[str, Any]]:
    if not path.exists():
        return {}
    metadata: dict[str, dict[str, Any]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        notice_id = str(payload.get("notice_id", ""))
        if notice_id:
            metadata[notice_id] = payload
    return metadata


def load_ocr_documents(path: Path, include_example: bool, notice_metadata: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    if path.name.endswith(".example.jsonl") and not include_example:
        return []

    documents: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        plain_text = str(payload.get("plain_text", "")).strip()
        if not plain_text:
            continue
        notice_id = str(payload.get("notice_id", ""))
        notice = notice_metadata.get(notice_id, {})
        documents.append(
            {
                "notice_id": notice_id,
                "source_image_path": str(payload.get("file_path", "")),
                "ocr_engine": str(payload.get("ocr_engine", "")),
                "engine_version": str(payload.get("engine_version", "")),
                "preprocess_profile": str(payload.get("preprocess_profile", "")),
                "title": infer_title(payload, notice),
                "url": payload.get("url") or notice.get("url"),
                "posted_at": payload.get("posted_at") or notice.get("posted_at"),
                "plain_text": plain_text,
            }
        )
    return documents


def infer_title(payload: dict[str, Any], notice: dict[str, Any]) -> str:
    metadata = payload.get("metadata")
    if isinstance(metadata, dict) and metadata.get("title"):
        return str(metadata["title"])
    if payload.get("title"):
        return str(payload["title"])
    if notice.get("title"):
        return str(notice["title"])
    notice_id = payload.get("notice_id") or "ocr_notice"
    return f"OCR plain text - {notice_id}"


def render_ocr_markdown(payload: dict[str, Any]) -> str:
    lines = [
        "---",
        f"notice_id: {payload['notice_id']}",
        f"source_image_path: {payload['source_image_path']}",
        f"ocr_engine: {payload['ocr_engine']}",
        f"engine_version: {payload['engine_version']}",
        f"preprocess_profile: {payload['preprocess_profile']}",
        "content_type: image_ocr_plain",
        f"title: {payload['title']}",
        f"url: {payload.get('url') or ''}",
        f"posted_at: {payload.get('posted_at') or ''}",
        "---",
        "",
        f"# {payload['title']}",
        "",
        payload["plain_text"].strip(),
        "",
    ]
    return "\n".join(lines)


def build_corpus_manifest(documents: list[CorpusDocument], ocr_results_path: Path) -> dict[str, Any]:
    return {
        "condition": "ocr_plain",
        "dataset_path": "data/processed/ocr_plain/corpus/*.md",
        "source_text_docs": relative(TEXT_DOCS_DIR),
        "source_ocr_results": relative(ocr_results_path),
        "policy": "Text notices, FAQ, and OCR plain_text documents are included. Layout blocks are excluded.",
        "excluded_data_types": ["ocr_layout_blocks", "layout_aware_chunks"],
        "documents": [asdict(doc) for doc in documents],
    }


def build_index_metadata(documents: list[CorpusDocument], ocr_results_path: Path) -> dict[str, Any]:
    chunk_count = sum(doc.chunk_count for doc in documents)
    ocr_docs = [doc for doc in documents if doc.content_type == "image_ocr_plain"]
    return {
        "condition": "ocr_plain",
        "index_name": INDEX_NAME,
        "corpus_profile": CORPUS_PROFILE,
        "index_version": INDEX_VERSION,
        "index_namespace": "indices/ocr_plain",
        "source_data_path": "file:data/processed/ocr_plain/corpus/*.md",
        "dataset_path": "data/processed/ocr_plain/corpus/*.md",
        "document_count": len(documents),
        "text_document_count": sum(1 for doc in documents if doc.content_type == "text"),
        "ocr_document_count": len(ocr_docs),
        "chunk_count": chunk_count,
        "ocr_chunk_count": sum(doc.chunk_count for doc in ocr_docs),
        "corpus_hash": corpus_hash(documents),
        "embedding_model": EMBEDDING_MODEL,
        "embedding_dim": EMBEDDING_DIM,
        "chunk_size": CHUNK_SIZE,
        "chunk_overlap": CHUNK_OVERLAP,
        "tokenizer": TOKENIZER,
        "bm25_tokenizer": BM25_TOKENIZER,
        "bm25_top_k": 50,
        "dense_top_k": 50,
        "final_top_k": 5,
        "fusion_method": "rrf",
        "rrf_k": 60,
        "retrieval_config_hash": sha256_text("|".join([
            "hybridTopK=5",
            "bm25TopK=50",
            "denseTopK=50",
            "rrfK=60",
            "fusionMethod=rrf",
            "minSimilarity=0.18",
            "refusalMinDenseScore=0.22",
            "refusalMinTokenOverlap=1",
            "refusalMinTokenCoverage=0.30",
            "refusalEvidenceTopK=40",
        ])),
        "dense_retrieval_mode": "in-memory-cosine-or-pgvector-runtime",
        "generation_model": "demo-extractive",
        "temperature": 0.1,
        "max_tokens": 350,
        "ocr_results_path": relative(ocr_results_path),
        "layout_used": False,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "notes": "Generated corpus metadata only. Run Spring reindex with APP_INDEX_NAME=ocr_plain to build BM25/dense runtime indexes.",
    }


def write_sample_questions(path: Path) -> None:
    rows = [
        {"question": "입학식 일정은 어디에서 확인할 수 있어?", "target": "image_ocr_plain"},
        {"question": "주차 서비스 요금은 어떻게 돼?", "target": "image_ocr_plain"},
        {"question": "이미지 공지에 나온 OT 안내 내용을 알려줘", "target": "image_ocr_plain"},
        {"question": "선행학습 영향평가 결과 보고서는 어디서 확인해?", "target": "pdf_or_text"},
        {"question": "입학전형 종료 안내에 나온 핵심 내용을 알려줘", "target": "pdf_or_text"},
    ]
    write_jsonl(path, rows)


def write_sample_results_placeholder(path: Path, ocr_documents: list[dict[str, Any]]) -> None:
    status = "pending_ocr_results" if not ocr_documents else "pending_runtime_reindex"
    rows = []
    for question in [
        "입학식 일정은 어디에서 확인할 수 있어?",
        "주차 서비스 요금은 어떻게 돼?",
        "이미지 공지에 나온 OT 안내 내용을 알려줘",
        "선행학습 영향평가 결과 보고서는 어디서 확인해?",
        "입학전형 종료 안내에 나온 핵심 내용을 알려줘",
    ]:
        rows.append({
            "question": question,
            "status": status,
            "reason": "Run OCR batch and Spring ocr_plain reindex before recording final OCR-RAG answers.",
        })
    write_jsonl(path, rows)


def write_readme(path: Path) -> None:
    path.write_text(
        """# OCR Plain RAG Index

This index namespace is for the OCR-RAG condition.

- Includes: text notices, FAQ, text guides, image OCR `plain_text`
- Excludes: OCR layout blocks, table structure, layout-aware chunks
- Runtime index name: `ocr_plain`
- Runtime corpus profile: `ocr_plain`
- Runtime index version: `ocr_plain_v1`

Build corpus:

```powershell
python scripts\\build_ocr_plain_corpus.py --ocr-results data/processed/ocr_outputs/ocr_results.jsonl
```

Run Spring reindex:

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_plain/corpus/*.md"
$env:APP_INDEX_NAME="ocr_plain"
$env:APP_CORPUS_PROFILE="ocr_plain"
$env:APP_INDEX_VERSION="ocr_plain_v1"
$env:APP_FORCE_REINDEX="true"
.\\gradlew.bat bootRun
```
""",
        encoding="utf-8",
        newline="\n",
    )


def count_chunks(text: str) -> int:
    sections = split_sections(text)
    return sum(len(chunk_section(section_text)) for _, section_text in sections)


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
    content = normalize("\n".join(buffer))
    if content:
        sections.append((title, content))
    buffer.clear()


def chunk_section(text: str) -> list[str]:
    paragraphs = [normalize(part) for part in re.split(r"\n\s*\n", text) if normalize(part)]
    if not paragraphs:
        paragraphs = [text]
    chunks: list[str] = []
    current = ""
    for paragraph in paragraphs:
        if len(paragraph) > CHUNK_SIZE:
            if current:
                chunks.append(current.strip())
                current = ""
            chunks.extend(paragraph[i:i + 620].strip() for i in range(0, len(paragraph), 620))
            continue
        candidate_len = len(current) + len(paragraph) + 2
        if current and candidate_len > CHUNK_SIZE and len(current) >= 180:
            chunks.append(current.strip())
            current = ""
        current = paragraph if not current else current + "\n\n" + paragraph
    if current.strip():
        chunks.append(current.strip())
    return chunks


def normalize(value: str) -> str:
    normalized = value.replace("\r", "")
    normalized = re.sub(r"[ \t]+", " ", normalized)
    normalized = re.sub(r"\n{3,}", "\n\n", normalized)
    return normalized.strip()


def corpus_hash(documents: list[CorpusDocument]) -> str:
    parts: list[str] = []
    for doc in documents:
        parts.extend([
            doc.document_id,
            doc.title,
            doc.source_path,
            doc.output_path,
            doc.content_type,
            doc.source_hash or "",
            str(doc.chunk_count),
        ])
    return sha256_text("\n".join(parts))


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def safe_filename(value: str) -> str:
    safe = re.sub(r"[^0-9A-Za-z가-힣_.-]+", "_", value.strip())
    return safe.strip("_") or "unknown"


def relative(path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(ROOT).as_posix()
    except ValueError:
        return resolved.as_posix()


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
