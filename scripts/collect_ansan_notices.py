#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone, timedelta
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib import robotparser
from urllib.parse import parse_qs, urljoin, urlparse

import requests


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = ROOT / "data" / "raw" / "ansan_notices"
DEFAULT_PROCESSED_DIR = ROOT / "data" / "processed"
DEFAULT_SEEDS = [
    "https://iphak.ansan.ac.kr/iphak/board/19",
    "https://iphak.ansan.ac.kr/iphak/board/21",
    "https://iphak.ansan.ac.kr/iphak/board/22",
]
BOARD_CATEGORIES = {
    "2": "수시1차 자료실",
    "4": "수시2차 자료실",
    "6": "정시 자료실",
    "7": "외국인 유학생 공지사항",
    "11": "산업체위탁 서식 자료실",
    "13": "전공심화 서식 자료실",
    "17": "편입학 서식 자료실",
    "19": "입학 공지사항",
    "20": "입학상담",
    "21": "입시 FAQ",
    "22": "입학전형시행계획",
    "23": "기타 입학자료",
    "35": "안산대학교 공지",
}
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff"}
PDF_EXTENSIONS = {".pdf"}
ATTACHMENT_EXTENSIONS = IMAGE_EXTENSIONS | PDF_EXTENSIONS | {
    ".hwp",
    ".hwpx",
    ".doc",
    ".docx",
    ".xls",
    ".xlsx",
    ".ppt",
    ".pptx",
    ".zip",
}
KST = timezone(timedelta(hours=9))
USER_AGENT = "Chat2BResearchCollector/0.1 (+https://iphak.ansan.ac.kr; academic-use)"


class VisibleTextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._parts: list[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:  # type: ignore[override]
        attr_map = {name.lower(): value for name, value in attrs}
        if tag in {"script", "style", "noscript"}:
            self._skip_depth += 1
            return
        if tag == "img" and attr_map.get("alt"):
            self._parts.append("\n")
            self._parts.append(attr_map["alt"])
            self._parts.append("\n")
        if tag in {"p", "div", "li", "br", "tr", "td", "th", "h1", "h2", "h3", "h4", "section", "article"}:
            self._parts.append("\n")

    def handle_endtag(self, tag: str) -> None:  # type: ignore[override]
        if tag in {"script", "style", "noscript"} and self._skip_depth:
            self._skip_depth -= 1
            return
        if tag in {"p", "div", "li", "br", "tr", "td", "th", "h1", "h2", "h3", "h4", "section", "article"}:
            self._parts.append("\n")

    def handle_data(self, data: str) -> None:  # type: ignore[override]
        if not self._skip_depth:
            self._parts.append(data)

    def text(self) -> str:
        text = unescape("".join(self._parts))
        lines: list[str] = []
        for raw in text.splitlines():
            line = re.sub(r"\s+", " ", raw).strip()
            if line and (not lines or lines[-1] != line):
                lines.append(line)
        return "\n".join(lines)


@dataclass
class NoticeManifestEntry:
    notice_id: str
    title: str
    url: str
    posted_at: str | None
    collected_at: str
    department: str
    category: str
    content_type: str
    raw_text: str
    attachment_urls: list[str]
    local_file_path: str
    source_hash: str
    downloaded_files: list[str]
    is_duplicate: bool
    duplicate_of: str | None


@dataclass
class FailedUrl:
    url: str
    reason: str
    collected_at: str


class RobotCache:
    def __init__(self, session: requests.Session) -> None:
        self.session = session
        self._cache: dict[str, robotparser.RobotFileParser | None] = {}

    def allowed(self, url: str) -> bool:
        parsed = urlparse(url)
        host = f"{parsed.scheme}://{parsed.netloc}"
        if host not in self._cache:
            robots_url = urljoin(host, "/robots.txt")
            parser = robotparser.RobotFileParser()
            try:
                response = self.session.get(robots_url, timeout=15)
                if response.status_code >= 400:
                    self._cache[host] = None
                else:
                    parser.parse(response.text.splitlines())
                    self._cache[host] = parser
            except requests.RequestException:
                self._cache[host] = None
        parser = self._cache[host]
        if parser is None:
            return True
        return parser.can_fetch(USER_AGENT, url)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_text(html: str) -> str:
    content = extract_content_region(html)
    parser = VisibleTextParser()
    parser.feed(content)
    return parser.text()


def extract_content_region(html: str) -> str:
    by_id = extract_balanced_div(html, r'id=["\']IContents_div내용["\']')
    if by_id:
        return by_id
    by_class = extract_balanced_div(html, r'class=["\'][^"\']*board-view-cont[^"\']*["\']')
    return by_class if by_class else html


def extract_balanced_div(html: str, marker_pattern: str) -> str | None:
    start_match = re.search(r"<div\b[^>]*" + marker_pattern + r"[^>]*>", html, re.IGNORECASE)
    if not start_match:
        return None
    depth = 0
    for token in re.finditer(r"</?div\b[^>]*>", html[start_match.start() :], re.IGNORECASE):
        token_text = token.group(0)
        if token_text.startswith("</"):
            depth -= 1
            if depth == 0:
                end = start_match.start() + token.end()
                return html[start_match.start() : end]
        else:
            depth += 1
    return None


def clean_html_text(value: str) -> str:
    parser = VisibleTextParser()
    parser.feed(value)
    return re.sub(r"\s+", " ", parser.text()).strip()


def parse_title(html: str) -> str:
    patterns = [
        r'<a[^>]+id=["\']IContents_atitle["\'][^>]*>([\s\S]*?)</a>',
        r'<h3[^>]*>\s*<a[^>]+class=["\'][^"\']*title[^"\']*["\'][^>]*>([\s\S]*?)</a>\s*</h3>',
        r'<title[^>]*>([\s\S]*?)</title>',
    ]
    for pattern in patterns:
        match = re.search(pattern, html, re.IGNORECASE)
        if match:
            title = clean_html_text(match.group(1))
            if title:
                return title
    return "제목 없음"


def parse_posted_at(html: str) -> str | None:
    candidates: list[str] = []
    span_start = re.search(r'id=["\']IContents_span작성일시["\']', html, re.IGNORECASE)
    if span_start:
        candidates.append(clean_html_text(html[span_start.start() : span_start.start() + 500]))
    candidates.append(clean_html_text(html[:8000]))
    for candidate in candidates:
        match = re.search(r"(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?", candidate)
        if not match:
            continue
        year, month, day, hour, minute = match.groups()
        dt = datetime(
            int(year),
            int(month),
            int(day),
            int(hour or 0),
            int(minute or 0),
            tzinfo=KST,
        )
        return dt.isoformat()
    return None


def parse_notice_id(url: str) -> str:
    parsed = urlparse(url)
    match = re.search(r"/([^/]+)/boardview/(\d+)/(\d+)", parsed.path)
    if match:
        site, board_id, item_id = match.groups()
        return f"{site}_{board_id}_{item_id}"
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
    return f"notice_{digest}"


def category_from_url(url: str) -> str:
    parsed = urlparse(url)
    if "/www/" in parsed.path:
        match = re.search(r"/boardview/(\d+)/", parsed.path)
        if match:
            return "안산대학교 공지사항"
    match = re.search(r"/boardview/(\d+)/", urlparse(url).path)
    if match:
        return BOARD_CATEGORIES.get(match.group(1), f"게시판 {match.group(1)}")
    match = re.search(r"/board/(\d+)", urlparse(url).path)
    if match:
        return BOARD_CATEGORIES.get(match.group(1), f"게시판 {match.group(1)}")
    return "공지"


def department_from_url(url: str) -> str:
    host = urlparse(url).netloc
    path = urlparse(url).path
    if "iphak.ansan.ac.kr" in host or "/iphak/" in path:
        return "입학처"
    if "ansan.ac.kr" in host:
        return "안산대학교"
    return host


def discover_notice_urls(seed_html: str, seed_url: str, allow_cross_host: bool) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()
    seed_host = urlparse(seed_url).netloc
    for href in re.findall(r'href=["\']([^"\']*?/boardview/\d+/\d+[^"\']*)["\']', seed_html, re.IGNORECASE):
        absolute = urljoin(seed_url, html_unescape_href(href))
        normalized = absolute.split("#", 1)[0]
        if not allow_cross_host and urlparse(normalized).netloc != seed_host:
            continue
        if normalized not in seen:
            seen.add(normalized)
            urls.append(normalized)
    return urls


def html_unescape_href(href: str) -> str:
    return unescape(href).replace("&amp;", "&")


def discover_attachment_urls(html: str, page_url: str) -> list[str]:
    region = extract_content_region(html)
    file_region = extract_balanced_div(html, r'id=["\']IContents_div첨부파일["\']')
    if file_region:
        region += "\n" + file_region

    candidates: list[str] = []
    for attr in ("href", "src"):
        for value in re.findall(attr + r'=["\']([^"\']+)["\']', region, re.IGNORECASE):
            value = html_unescape_href(value.strip())
            if not value or value.startswith("#") or value.lower().startswith("javascript:"):
                continue
            absolute = normalize_attachment_url(urljoin(page_url, value))
            suffix = Path(urlparse(absolute).path).suffix.lower()
            if suffix in ATTACHMENT_EXTENSIONS:
                if "/site/resource/" in urlparse(absolute).path:
                    continue
                candidates.append(absolute)
    return unique(candidates)


def normalize_attachment_url(url: str) -> str:
    parsed = urlparse(url)
    file_values = parse_qs(parsed.query).get("file", [])
    if file_values:
        file_url = file_values[0]
        if file_url.startswith("/"):
            return urljoin(f"{parsed.scheme}://{parsed.netloc}", file_url)
        return urljoin(url, file_url)
    return url


def unique(items: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        if item not in seen:
            result.append(item)
            seen.add(item)
    return result


def classify_content_type(raw_text: str, attachment_urls: list[str]) -> str:
    has_image = any(Path(urlparse(url).path).suffix.lower() in IMAGE_EXTENSIONS for url in attachment_urls)
    has_pdf = any(Path(urlparse(url).path).suffix.lower() in PDF_EXTENSIONS for url in attachment_urls)
    has_meaningful_text = len(raw_text.replace("\n", " ").strip()) >= 120
    if (has_image or has_pdf) and has_meaningful_text:
        return "mixed"
    if has_image and has_pdf:
        return "mixed"
    if has_image:
        return "image"
    if has_pdf:
        return "pdf"
    return "text"


def safe_suffix(url: str, fallback: str = ".bin") -> str:
    suffix = Path(urlparse(url).path).suffix.lower()
    return suffix if suffix else fallback


def relative_to_root(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def download_attachment(
    session: requests.Session,
    robot_cache: RobotCache,
    url: str,
    notice_id: str,
    output_dir: Path,
    seen_file_hashes: dict[str, Path],
    delay: float,
    max_attachment_bytes: int,
) -> Path | None:
    if not robot_cache.allowed(url):
        return None
    response = session.get(url, timeout=30)
    response.raise_for_status()
    data = response.content
    if len(data) > max_attachment_bytes:
        return None
    file_hash = sha256_bytes(data)
    if file_hash in seen_file_hashes:
        return seen_file_hashes[file_hash]
    suffix = safe_suffix(url)
    if suffix in IMAGE_EXTENSIONS:
        target_dir = output_dir / "images"
    elif suffix in PDF_EXTENSIONS:
        target_dir = output_dir / "pdfs"
    else:
        target_dir = output_dir / "files"
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / f"{notice_id}_{file_hash[:10]}{suffix}"
    target.write_bytes(data)
    seen_file_hashes[file_hash] = target
    time.sleep(delay)
    return target


def collect_notice(
    session: requests.Session,
    robot_cache: RobotCache,
    notice_url: str,
    output_dir: Path,
    seen_source_hashes: dict[str, str],
    seen_file_hashes: dict[str, Path],
    delay: float,
    max_attachment_bytes: int,
) -> NoticeManifestEntry:
    if not robot_cache.allowed(notice_url):
        raise RuntimeError("robots.txt disallows this URL")

    response = session.get(notice_url, timeout=30)
    response.raise_for_status()
    response.encoding = response.encoding or "utf-8"
    html_bytes = response.content
    html_text = response.text
    source_hash = sha256_bytes(html_bytes)
    notice_id = parse_notice_id(response.url)
    html_path = output_dir / "html" / f"{notice_id}.html"
    html_path.parent.mkdir(parents=True, exist_ok=True)
    html_path.write_bytes(html_bytes)

    raw_text = normalize_text(html_text)
    attachment_urls = discover_attachment_urls(html_text, response.url)
    downloaded_files: list[str] = []
    for attachment_url in attachment_urls:
        try:
            downloaded = download_attachment(
                session,
                robot_cache,
                attachment_url,
                notice_id,
                output_dir,
                seen_file_hashes,
                delay,
                max_attachment_bytes,
            )
            if downloaded:
                downloaded_files.append(relative_to_root(downloaded))
        except requests.RequestException:
            continue

    duplicate_of = seen_source_hashes.get(source_hash)
    if duplicate_of is None:
        seen_source_hashes[source_hash] = notice_id

    collected_at = datetime.now(timezone.utc).isoformat()
    return NoticeManifestEntry(
        notice_id=notice_id,
        title=parse_title(html_text),
        url=response.url,
        posted_at=parse_posted_at(html_text),
        collected_at=collected_at,
        department=department_from_url(response.url),
        category=category_from_url(response.url),
        content_type=classify_content_type(raw_text, attachment_urls),
        raw_text=raw_text,
        attachment_urls=attachment_urls,
        local_file_path=relative_to_root(html_path),
        source_hash=source_hash,
        downloaded_files=unique(downloaded_files),
        is_duplicate=duplicate_of is not None,
        duplicate_of=duplicate_of,
    )


def ensure_directories(output_dir: Path, processed_dir: Path) -> None:
    for child in ("html", "images", "pdfs", "files"):
        (output_dir / child).mkdir(parents=True, exist_ok=True)
    for child in ("text_only", "ocr_plain", "ocr_layout"):
        target = processed_dir / child
        target.mkdir(parents=True, exist_ok=True)
        keep = target / ".gitkeep"
        if not keep.exists():
            keep.write_text("", encoding="utf-8")


def write_jsonl(path: Path, rows: Iterable[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def write_manifest_schema(path: Path) -> None:
    schema = {
        "description": "안산대학교 공지 원천 데이터 manifest. Text-only, OCR-RAG, Layout-aware OCR-RAG 전처리 전 단계의 보존용 metadata입니다.",
        "format": "jsonl",
        "required_fields": {
            "notice_id": "수집기가 생성한 안정적인 공지 ID. 예: iphak_19_649",
            "title": "공지 제목",
            "url": "공지 상세 페이지 URL",
            "posted_at": "공지 게시일. 확인 불가 시 null",
            "collected_at": "수집 시각. UTC ISO-8601",
            "department": "공지 출처 부서 또는 학과",
            "category": "게시판 또는 공지 분류",
            "content_type": "text, image, pdf, mixed 중 하나",
            "raw_text": "HTML 본문에서 추출한 UTF-8 plain text. OCR 결과는 포함하지 않음",
            "attachment_urls": "본문/첨부 영역에서 발견한 직접 접근 가능한 이미지/PDF/파일 URL",
            "local_file_path": "보존한 원본 HTML의 로컬 경로",
            "source_hash": "원본 HTML bytes SHA-256. 중복 URL/문서 감지 기준",
        },
        "additional_fields": {
            "downloaded_files": "직접 다운로드에 성공한 이미지/PDF/파일 로컬 경로 목록",
            "is_duplicate": "source_hash가 기존 문서와 같은지 여부",
            "duplicate_of": "중복이면 원본 notice_id",
        },
    }
    path.write_text(json.dumps(schema, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect Ansan University notice raw data for RAG experiments.")
    parser.add_argument("--seed", action="append", default=[], help="Board/list URL to crawl. Can be passed multiple times.")
    parser.add_argument("--max-notices", type=int, default=10, help="Maximum notice detail pages to collect.")
    parser.add_argument("--delay", type=float, default=1.0, help="Delay in seconds between network requests.")
    parser.add_argument("--allow-cross-host", action="store_true", help="Allow detail links that move to another ansan.ac.kr host.")
    parser.add_argument("--max-attachment-mb", type=float, default=25.0, help="Skip downloading individual attachments larger than this size. URLs are still recorded.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_DIR, help="Raw output directory.")
    parser.add_argument("--processed-dir", type=Path, default=DEFAULT_PROCESSED_DIR, help="Processed data root directory.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = args.output.resolve()
    processed_dir = args.processed_dir.resolve()
    ensure_directories(output_dir, processed_dir)
    write_manifest_schema(output_dir / "manifest_schema.json")

    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT})
    robot_cache = RobotCache(session)
    seeds = args.seed or DEFAULT_SEEDS
    failed: list[FailedUrl] = []
    notice_urls: list[str] = []

    for seed_url in seeds:
        collected_at = datetime.now(timezone.utc).isoformat()
        try:
            if not robot_cache.allowed(seed_url):
                failed.append(FailedUrl(seed_url, "robots.txt disallows seed URL", collected_at))
                continue
            response = session.get(seed_url, timeout=30)
            response.raise_for_status()
            response.encoding = response.encoding or "utf-8"
            notice_urls.extend(discover_notice_urls(response.text, response.url, args.allow_cross_host))
            time.sleep(args.delay)
        except requests.RequestException as exc:
            failed.append(FailedUrl(seed_url, f"seed fetch failed: {exc}", collected_at))

    notice_urls = unique(notice_urls)[: args.max_notices]
    seen_source_hashes: dict[str, str] = {}
    seen_file_hashes: dict[str, Path] = {}
    entries: list[NoticeManifestEntry] = []
    max_attachment_bytes = int(args.max_attachment_mb * 1024 * 1024)

    for notice_url in notice_urls:
        collected_at = datetime.now(timezone.utc).isoformat()
        try:
            entries.append(
                collect_notice(
                    session,
                    robot_cache,
                    notice_url,
                    output_dir,
                    seen_source_hashes,
                    seen_file_hashes,
                    args.delay,
                    max_attachment_bytes,
                )
            )
            time.sleep(args.delay)
        except Exception as exc:
            failed.append(FailedUrl(notice_url, str(exc), collected_at))

    write_jsonl(output_dir / "manifest.jsonl", (asdict(entry) for entry in entries))
    write_jsonl(output_dir / "failed_urls.jsonl", (asdict(item) for item in failed))

    counts: dict[str, int] = {"text": 0, "image": 0, "pdf": 0, "mixed": 0}
    for entry in entries:
        if not entry.is_duplicate:
            counts[entry.content_type] = counts.get(entry.content_type, 0) + 1

    print(json.dumps(
        {
            "manifest": relative_to_root(output_dir / "manifest.jsonl"),
            "manifest_schema": relative_to_root(output_dir / "manifest_schema.json"),
            "collected": len(entries),
            "failed": len(failed),
            "counts_excluding_duplicates": counts,
            "sample_notice_ids": [entry.notice_id for entry in entries[:5]],
        },
        ensure_ascii=False,
        indent=2,
    ))


if __name__ == "__main__":
    main()
