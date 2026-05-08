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
DEFAULT_OUTPUT_DIR = ROOT / "data" / "processed" / "ocr_layout" / "corpus"
DEFAULT_INDEX_DIR = ROOT / "indices" / "ocr_layout"
DEFAULT_NOTICE_MANIFEST = ROOT / "data" / "raw" / "ansan_notices" / "manifest.jsonl"
CHUNK_SIZE = 700
CHUNK_OVERLAP = 0
EMBEDDING_MODEL = "hashed-256"
EMBEDDING_DIM = 256
TOKENIZER = "unicode-letter-number-v1"
BM25_TOKENIZER = "unicode-korean-v1"
INDEX_NAME = "ocr_layout"
INDEX_VERSION = "ocr_layout_v1"
CORPUS_PROFILE = "ocr_layout"
DATE_PATTERN = re.compile(
    r"(?:(?:20\d{2})[.\-/년\s]+(?:0?[1-9]|1[0-2])[.\-/월\s]+(?:0?[1-9]|[12]\d|3[01])(?:일)?)|"
    r"(?:(?:0?[1-9]|1[0-2])[.\-/월\s]+(?:0?[1-9]|[12]\d|3[01])(?:일)?)"
)
PHONE_PATTERN = re.compile(r"(?:0\d{1,2}[-.)\s]?\d{3,4}[-.\s]?\d{4})")
EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


@dataclass
class CorpusDocument:
    document_id: str
    title: str
    source_path: str
    output_path: str
    content_type: str
    source_hash: str | None = None
    chunk_count: int = 0


@dataclass
class LayoutChunk:
    chunk_id: str
    notice_id: str
    source_image_path: str
    ocr_engine: str
    engine_version: str
    preprocess_profile: str
    block_type: str
    bbox: list[float]
    page: int
    reading_order: int
    confidence: float
    title: str
    url: str | None
    posted_at: str | None
    extracted_dates: list[str]
    extracted_contacts: list[str]
    text: str
    markdown_text: str
    output_path: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build layout-aware OCR-RAG corpus without touching text_only or ocr_plain.")
    parser.add_argument("--text-docs", type=Path, default=TEXT_DOCS_DIR)
    parser.add_argument("--ocr-results", type=Path, default=DEFAULT_OCR_RESULTS)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--index-dir", type=Path, default=DEFAULT_INDEX_DIR)
    parser.add_argument("--notice-manifest", type=Path, default=DEFAULT_NOTICE_MANIFEST)
    parser.add_argument("--include-example", action="store_true", help="Allow ocr_results.example.jsonl for schema demos only.")
    parser.add_argument("--min-confidence", type=float, default=0.0, help="Keep low confidence chunks but mark them in metadata.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = args.output_dir.resolve()
    index_dir = args.index_dir.resolve()
    reset_dir(output_dir)
    index_dir.mkdir(parents=True, exist_ok=True)

    documents: list[CorpusDocument] = []
    for path in sorted(args.text_docs.glob("*.md")):
        target = output_dir / "text" / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
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
                chunk_count=count_text_chunks(text),
            )
        )

    notice_metadata = load_notice_metadata(args.notice_manifest.resolve())
    layout_chunks = load_layout_chunks(args.ocr_results.resolve(), args.include_example, notice_metadata, args.min_confidence)
    for chunk in layout_chunks:
        target = output_dir / "layout_chunks" / f"{safe_filename(chunk.chunk_id)}.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        chunk.output_path = relative(target)
        target.write_text(render_layout_chunk_markdown(chunk), encoding="utf-8", newline="\n")
        documents.append(
            CorpusDocument(
                document_id=chunk.chunk_id,
                title=chunk.title,
                source_path=chunk.source_image_path,
                output_path=relative(target),
                content_type="image_ocr_layout_chunk",
                source_hash=sha256_text(chunk.markdown_text),
                chunk_count=1,
            )
        )

    write_jsonl(index_dir / "layout_chunks.jsonl", (asdict(chunk) for chunk in layout_chunks))
    write_jsonl(index_dir / "layout_chunk_examples.jsonl", build_schema_examples())
    write_json(index_dir / "corpus_manifest.json", build_corpus_manifest(documents, layout_chunks, args.ocr_results.resolve()))
    write_json(index_dir / "index_metadata.json", build_index_metadata(documents, layout_chunks, args.ocr_results.resolve()))
    write_sample_questions(index_dir / "sample_questions.jsonl")
    write_sample_results_placeholder(index_dir / "sample_results.pending.jsonl", layout_chunks)
    write_readme(index_dir / "README.md")

    summary = {
        "condition": "ocr_layout",
        "documents": len(documents),
        "text_documents": sum(1 for doc in documents if doc.content_type == "text"),
        "layout_chunk_documents": sum(1 for doc in documents if doc.content_type == "image_ocr_layout_chunk"),
        "chunks": sum(doc.chunk_count for doc in documents),
        "layout_chunks": len(layout_chunks),
        "dates": sorted({date for chunk in layout_chunks for date in chunk.extracted_dates}),
        "contacts": sorted({contact for chunk in layout_chunks for contact in chunk.extracted_contacts}),
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


def load_layout_chunks(
    path: Path,
    include_example: bool,
    notice_metadata: dict[str, dict[str, Any]],
    min_confidence: float,
) -> list[LayoutChunk]:
    if not path.exists():
        return []
    if path.name.endswith(".example.jsonl") and not include_example:
        return []

    chunks: list[LayoutChunk] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        notice_id = str(payload.get("notice_id", ""))
        notice = notice_metadata.get(notice_id, {})
        title = infer_title(payload, notice)
        source_image_path = str(payload.get("file_path", ""))
        blocks = payload.get("blocks", [])
        if not isinstance(blocks, list):
            continue
        sorted_blocks = sorted(blocks, key=block_sort_key)
        for reading_order, block in enumerate(sorted_blocks, start=1):
            text = normalize(str(block.get("text", "")))
            if not text:
                continue
            confidence = float(block.get("confidence", 0.0) or 0.0)
            block_type = classify_block_type(text, str(block.get("block_type", "unknown")), confidence)
            markdown_text = render_block_text(text, block_type)
            extracted_dates = extract_dates(text)
            extracted_contacts = extract_contacts(text)
            low_confidence = confidence < min_confidence
            chunk_id = "_".join([
                "layout",
                safe_filename(notice_id or "notice"),
                str(int(block.get("page", 1) or 1)),
                f"{reading_order:04d}",
            ])
            if low_confidence:
                markdown_text = "[LOW_CONFIDENCE]\n" + markdown_text
            chunks.append(
                LayoutChunk(
                    chunk_id=chunk_id,
                    notice_id=notice_id,
                    source_image_path=source_image_path,
                    ocr_engine=str(payload.get("ocr_engine", "")),
                    engine_version=str(payload.get("engine_version", "")),
                    preprocess_profile=str(payload.get("preprocess_profile", "")),
                    block_type=block_type,
                    bbox=normalize_bbox(block.get("bbox")),
                    page=int(block.get("page", 1) or 1),
                    reading_order=reading_order,
                    confidence=confidence,
                    title=title,
                    url=payload.get("url") or notice.get("url"),
                    posted_at=payload.get("posted_at") or notice.get("posted_at"),
                    extracted_dates=extracted_dates,
                    extracted_contacts=extracted_contacts,
                    text=text,
                    markdown_text=markdown_text,
                    output_path="",
                )
            )
    return chunks


def block_sort_key(block: dict[str, Any]) -> tuple[int, float, float]:
    bbox = normalize_bbox(block.get("bbox"))
    return (int(block.get("page", 1) or 1), bbox[1], bbox[0])


def normalize_bbox(value: Any) -> list[float]:
    if isinstance(value, list) and len(value) >= 4:
        try:
            return [float(value[0]), float(value[1]), float(value[2]), float(value[3])]
        except (TypeError, ValueError):
            pass
    return [0.0, 0.0, 0.0, 0.0]


def infer_title(payload: dict[str, Any], notice: dict[str, Any]) -> str:
    metadata = payload.get("metadata")
    if isinstance(metadata, dict) and metadata.get("title"):
        return str(metadata["title"])
    if payload.get("title"):
        return str(payload["title"])
    if notice.get("title"):
        return str(notice["title"])
    notice_id = payload.get("notice_id") or "ocr_notice"
    return f"Layout-aware OCR - {notice_id}"


def classify_block_type(text: str, raw_type: str, confidence: float) -> str:
    raw = raw_type.lower().strip()
    if raw in {"title", "table"}:
        return raw
    if extract_contacts(text):
        return "contact"
    if extract_dates(text) and len(text) <= 80:
        return "date"
    if looks_like_table(text):
        return "table"
    if raw in {"body", "text"}:
        return "body"
    if confidence <= 0:
        return "unknown"
    return "body"


def looks_like_table(text: str) -> bool:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if len(lines) >= 2 and any("\t" in line or "|" in line for line in lines):
        return True
    if len(re.findall(r"\s{2,}", text)) >= 3:
        return True
    return False


def render_block_text(text: str, block_type: str) -> str:
    if block_type == "table":
        return render_table_like(text)
    if block_type == "title":
        return f"# {text}"
    return text


def render_table_like(text: str) -> str:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if len(lines) >= 2:
        rows = [split_table_row(line) for line in lines]
        max_cols = max(len(row) for row in rows)
        if max_cols >= 2:
            normalized = [row + [""] * (max_cols - len(row)) for row in rows]
            header = normalized[0]
            body = normalized[1:]
            markdown = [
                "| " + " | ".join(header) + " |",
                "| " + " | ".join(["---"] * max_cols) + " |",
            ]
            markdown.extend("| " + " | ".join(row) + " |" for row in body)
            return "\n".join(markdown)
    key_values = []
    for line in lines or [text]:
        if ":" in line:
            key, value = line.split(":", 1)
            key_values.append((key.strip(), value.strip()))
    if key_values:
        rows = ["| 항목 | 값 |", "| --- | --- |"]
        rows.extend(f"| {key} | {value} |" for key, value in key_values)
        return "\n".join(rows)
    return text


def split_table_row(line: str) -> list[str]:
    if "|" in line:
        return [part.strip() for part in line.strip("|").split("|")]
    if "\t" in line:
        return [part.strip() for part in line.split("\t")]
    return [part.strip() for part in re.split(r"\s{2,}", line) if part.strip()]


def extract_dates(text: str) -> list[str]:
    return unique(match.group(0).strip() for match in DATE_PATTERN.finditer(text))


def extract_contacts(text: str) -> list[str]:
    contacts = [match.group(0).strip() for match in PHONE_PATTERN.finditer(text)]
    contacts.extend(match.group(0).strip() for match in EMAIL_PATTERN.finditer(text))
    return unique(contacts)


def render_layout_chunk_markdown(chunk: LayoutChunk) -> str:
    lines = [
        "---",
        f"notice_id: {chunk.notice_id}",
        f"source_image_path: {chunk.source_image_path}",
        f"ocr_engine: {chunk.ocr_engine}",
        f"engine_version: {chunk.engine_version}",
        f"preprocess_profile: {chunk.preprocess_profile}",
        "content_type: image_ocr_layout_chunk",
        f"block_type: {chunk.block_type}",
        f"bbox: {json.dumps(chunk.bbox, ensure_ascii=False)}",
        f"page: {chunk.page}",
        f"reading_order: {chunk.reading_order}",
        f"confidence: {chunk.confidence}",
        f"title: {chunk.title}",
        f"url: {chunk.url or ''}",
        f"posted_at: {chunk.posted_at or ''}",
        f"extracted_dates: {json.dumps(chunk.extracted_dates, ensure_ascii=False)}",
        f"extracted_contacts: {json.dumps(chunk.extracted_contacts, ensure_ascii=False)}",
        "---",
        "",
        f"# {chunk.title}",
        "",
        f"- block_type: {chunk.block_type}",
        f"- reading_order: {chunk.reading_order}",
        f"- confidence: {chunk.confidence:.4f}",
        f"- bbox: {chunk.bbox}",
        "",
        chunk.markdown_text,
        "",
    ]
    return "\n".join(lines)


def build_corpus_manifest(documents: list[CorpusDocument], chunks: list[LayoutChunk], ocr_results_path: Path) -> dict[str, Any]:
    return {
        "condition": "ocr_layout",
        "dataset_path": "data/processed/ocr_layout/corpus/**/*.md",
        "source_text_docs": relative(TEXT_DOCS_DIR),
        "source_ocr_results": relative(ocr_results_path),
        "policy": "Text notices, FAQ, and layout-aware OCR chunks are included. OCR plain-only documents are excluded.",
        "included_data_types": ["text", "image_ocr_layout_chunk"],
        "excluded_data_types": ["image_ocr_plain"],
        "layout_chunk_count": len(chunks),
        "documents": [asdict(doc) for doc in documents],
    }


def build_index_metadata(documents: list[CorpusDocument], chunks: list[LayoutChunk], ocr_results_path: Path) -> dict[str, Any]:
    return {
        "condition": "ocr_layout",
        "index_name": INDEX_NAME,
        "corpus_profile": CORPUS_PROFILE,
        "index_version": INDEX_VERSION,
        "index_namespace": "indices/ocr_layout",
        "source_data_path": "file:data/processed/ocr_layout/corpus/**/*.md",
        "dataset_path": "data/processed/ocr_layout/corpus/**/*.md",
        "document_count": len(documents),
        "text_document_count": sum(1 for doc in documents if doc.content_type == "text"),
        "layout_chunk_document_count": sum(1 for doc in documents if doc.content_type == "image_ocr_layout_chunk"),
        "chunk_count": sum(doc.chunk_count for doc in documents),
        "layout_chunk_count": len(chunks),
        "block_type_counts": block_type_counts(chunks),
        "low_confidence_chunk_count": sum(1 for chunk in chunks if chunk.confidence < 0.5),
        "extracted_dates": sorted({date for chunk in chunks for date in chunk.extracted_dates}),
        "extracted_contacts": sorted({contact for chunk in chunks for contact in chunk.extracted_contacts}),
        "corpus_hash": corpus_hash(documents, chunks),
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
        "layout_used": True,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "notes": "Generated corpus metadata only. Run Spring reindex with APP_INDEX_NAME=ocr_layout to build BM25/dense runtime indexes.",
    }


def block_type_counts(chunks: list[LayoutChunk]) -> dict[str, int]:
    counts = {key: 0 for key in ["title", "body", "table", "date", "contact", "unknown"]}
    for chunk in chunks:
        counts[chunk.block_type] = counts.get(chunk.block_type, 0) + 1
    return counts


def build_schema_examples() -> list[dict[str, Any]]:
    examples = [
        ("title", "2026학년도 입학식 안내", [], []),
        ("body", "신입생 오리엔테이션은 지정된 장소에서 진행됩니다.", [], []),
        ("table", "| 구분 | 일정 |\n| --- | --- |\n| 원서접수 | 2026.01.10 |", ["2026.01.10"], []),
        ("date", "마감일: 2026년 2월 27일", ["2026년 2월 27일"], []),
        ("contact", "문의: 031-363-7700 / admissions@example.ac.kr", [], ["031-363-7700", "admissions@example.ac.kr"]),
    ]
    rows = []
    for index, (block_type, text, dates, contacts) in enumerate(examples, start=1):
        rows.append({
            "chunk_id": f"schema_example_{index}",
            "notice_id": "schema_example",
            "source_image_path": "data/raw/ansan_notices/images/example.png",
            "ocr_engine": "example",
            "preprocess_profile": "none",
            "block_type": block_type,
            "bbox": [10, 20 * index, 300, 20 * index + 30],
            "page": 1,
            "reading_order": index,
            "confidence": 0.95,
            "title": "Layout-aware chunk schema example",
            "url": "",
            "posted_at": "",
            "extracted_dates": dates,
            "extracted_contacts": contacts,
            "text": text,
            "markdown_text": text,
            "output_path": "",
            "is_schema_example": True,
        })
    return rows


def write_sample_questions(path: Path) -> None:
    rows = [
        {"question": "이미지 공지에서 입학식 날짜와 장소를 알려줘", "target": "date_or_body_block"},
        {"question": "표에 나온 접수 기간을 알려줘", "target": "table_block"},
        {"question": "공지 이미지에 적힌 문의 전화번호를 알려줘", "target": "contact_block"},
        {"question": "OCR confidence가 낮은 부분이 있는지 확인해줘", "target": "low_confidence_chunk"},
        {"question": "이미지 공지 제목과 본문 핵심 내용을 구분해서 알려줘", "target": "title_body_blocks"},
    ]
    write_jsonl(path, rows)


def write_sample_results_placeholder(path: Path, chunks: list[LayoutChunk]) -> None:
    status = "pending_ocr_results" if not chunks else "pending_runtime_reindex"
    rows = []
    for question in [
        "이미지 공지에서 입학식 날짜와 장소를 알려줘",
        "표에 나온 접수 기간을 알려줘",
        "공지 이미지에 적힌 문의 전화번호를 알려줘",
        "OCR confidence가 낮은 부분이 있는지 확인해줘",
        "이미지 공지 제목과 본문 핵심 내용을 구분해서 알려줘",
    ]:
        rows.append({
            "question": question,
            "status": status,
            "reason": "Run OCR batch with blocks and Spring ocr_layout reindex before recording final Layout-aware OCR-RAG answers.",
        })
    write_jsonl(path, rows)


def write_readme(path: Path) -> None:
    path.write_text(
        """# Layout-aware OCR-RAG Index

This index namespace is for the Layout-aware OCR-RAG condition.

- Includes: text notices, FAQ, text guides, OCR blocks with layout metadata
- Excludes: OCR plain-only documents
- Runtime index name: `ocr_layout`
- Runtime corpus profile: `ocr_layout`
- Runtime index version: `ocr_layout_v1`

Build corpus:

```powershell
python scripts\\build_ocr_layout_corpus.py --ocr-results data/processed/ocr_outputs/ocr_results.jsonl
```

Run Spring reindex:

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_layout/corpus/**/*.md"
$env:APP_INDEX_NAME="ocr_layout"
$env:APP_CORPUS_PROFILE="ocr_layout"
$env:APP_INDEX_VERSION="ocr_layout_v1"
$env:APP_FORCE_REINDEX="true"
.\\gradlew.bat bootRun
```
""",
        encoding="utf-8",
        newline="\n",
    )


def count_text_chunks(text: str) -> int:
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


def corpus_hash(documents: list[CorpusDocument], chunks: list[LayoutChunk]) -> str:
    parts: list[str] = []
    for doc in documents:
        parts.extend([doc.document_id, doc.title, doc.output_path, doc.content_type, doc.source_hash or "", str(doc.chunk_count)])
    for chunk in chunks:
        parts.extend([
            chunk.chunk_id,
            chunk.block_type,
            json.dumps(chunk.bbox),
            str(chunk.page),
            str(chunk.reading_order),
            str(chunk.confidence),
            chunk.text,
        ])
    return sha256_text("\n".join(parts))


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def safe_filename(value: str) -> str:
    safe = re.sub(r"[^0-9A-Za-z가-힣_.-]+", "_", value.strip())
    return safe.strip("_") or "unknown"


def unique(items: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        if item and item not in seen:
            result.append(item)
            seen.add(item)
    return result


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
