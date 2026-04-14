#!/usr/bin/env python3
from __future__ import annotations

import re
from dataclasses import dataclass
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin, urlparse

import requests


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "src" / "main" / "resources" / "admissions-docs"
ADMISSIONS_BASE = "https://iphak.ansan.ac.kr"
ADMISSIONS_HOME_URL = f"{ADMISSIONS_BASE}/iphak"
DEPARTMENT_LIST_URL = f"{ADMISSIONS_BASE}/iphak/dept_type1_total"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Safari/537.36"


COMMON_SKIP = {
    "Skip Menu",
    "주메뉴 바로가기",
    "본문 바로가기",
    "로그인",
    "안산대학교",
    "입학안내",
    "ENGLISH",
    "CHINESE",
    "전체메뉴",
    "전체 메뉴 닫기",
    "close",
    "home",
    "메인",
    "공유",
    "share",
    "Facebook",
    "Twitter",
    "KakaoTalk",
    "Band",
    "print",
    "개인정보처리방침",
    "이메일주소무단수집거부",
    "관련 사이트",
    "대학홈페이지",
    "입학처 홈페이지",
    "입학처 카카오채널",
}

STOP_MARKERS = {"개인정보처리방침"}
CATEGORY_ORDER = [
    "간호계열",
    "보건계열",
    "휴먼케어계열",
    "식품영양조리계열",
    "인문사회계열",
    "비즈니스계열",
    "컴퓨터계열",
    "디자인융합계열",
    "자율계열",
    "글로컬상생계열(성인학습자)",
]
SECTION_PATTERNS = [
    ("학과개요", ["학과개요", "학과소개", "전공소개", "전공개요"]),
    ("비전/교육목표", ["비전", "목표", "교육목표"]),
    ("교육과정", ["교육과정", "교육과정로드맵", "교과목개요"]),
    ("졸업 후 진로", ["졸업 후 진로", "진로", "취업", "진출", "자격증"]),
    ("학과 사무실", ["학과 사무실", "학과사무실"]),
]


class VisibleTextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._skip_depth = 0
        self._parts: list[str] = []

    def handle_starttag(self, tag: str, attrs) -> None:  # type: ignore[override]
        if tag in {"script", "style", "noscript"}:
            self._skip_depth += 1
            return
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

    def lines(self) -> list[str]:
        text = unescape("".join(self._parts))
        normalized = []
        for raw in text.splitlines():
            line = re.sub(r"\s+", " ", raw).strip()
            if line:
                normalized.append(line)
        return normalized


@dataclass
class Department:
    category: str
    name: str
    source_href: str


@dataclass
class Section:
    label: str
    url: str
    bullets: list[str]


def unique(seq: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in seq:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def fetch(session: requests.Session, url: str) -> requests.Response:
    response = session.get(url, timeout=25, allow_redirects=True)
    response.raise_for_status()
    return response


def html_title(html: str) -> str:
    match = re.search(r"<title>\s*(.*?)\s*</title>", html, re.I | re.S)
    if not match:
        return ""
    return re.sub(r"\s+", " ", unescape(match.group(1))).strip()


def visible_lines(html: str) -> list[str]:
    parser = VisibleTextParser()
    parser.feed(html)
    lines = parser.lines()
    cleaned: list[str] = []
    for line in lines:
        if cleaned and cleaned[-1] == line:
            continue
        cleaned.append(line)
    return cleaned


def clean_inner_text(html: str) -> str:
    parser = VisibleTextParser()
    parser.feed(html)
    lines = parser.lines()
    return " ".join(lines).strip()


def parse_department_cards(html: str) -> list[Department]:
    cards = re.findall(
        r'<h5 class="card-title">(.*?)</h5>.*?<ul class="list-group list-group-flush">(.*?)</ul>',
        html,
        re.S,
    )
    departments: list[Department] = []
    for raw_category, raw_items in cards:
        category = clean_inner_text(raw_category)
        for href, name in re.findall(r'<a href="(.*?)"[^>]*>(.*?)</a>', raw_items, re.S):
            departments.append(
                Department(
                    category=category,
                    name=clean_inner_text(name),
                    source_href=href.strip(),
                )
            )
    return departments


def parse_menu_items(html: str, page_url: str) -> list[tuple[str, str]]:
    pairs = re.findall(r'<a[^>]*href="([^"]+)"[^>]*role="menuitem"[^>]*>(.*?)</a>', html, re.I | re.S)
    items: list[tuple[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for href, raw_label in pairs:
        label = clean_inner_text(raw_label)
        if not label:
            continue
        if href.startswith("javascript:") or href.startswith("#"):
            continue
        url = urljoin(page_url, href)
        if "eclass.ansan.ac.kr" in url:
            continue
        key = (label, url)
        if key in seen:
            continue
        seen.add(key)
        items.append(key)
    return items


def parse_program_meta() -> dict[str, dict[str, str]]:
    target = next(DOCS_DIR.glob("*모집단위*.md"))
    meta: dict[str, dict[str, str]] = {}
    pattern = re.compile(r"^- (.*?): ([^,\n]+?)(?:, 입학정원 (\d+))?$")
    for line in target.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line.strip())
        if not match:
            continue
        name, duration, capacity = match.groups()
        entry = meta.setdefault(name, {})
        if duration and "년제" in duration and "duration" not in entry:
            entry["duration"] = duration
        if capacity and "capacity" not in entry:
            entry["capacity"] = capacity
    return meta


def should_skip_line(line: str, dept_name: str, menu_labels: set[str], title_line: str) -> bool:
    stripped = line.strip()
    if not stripped:
        return True
    if stripped in COMMON_SKIP or stripped in menu_labels:
        return True
    if stripped == dept_name or stripped == title_line:
        return True
    if stripped.startswith("COPYRIGHT"):
        return True
    if stripped.startswith("최종수정일"):
        return True
    if re.fullmatch(r"\d{4}\.\d{2}\.\d{2}", stripped):
        return True
    if stripped in {"*"}:
        return True
    if stripped.startswith("Dept. of") or stripped.startswith("Department of"):
        return True
    if stripped.lower() in {"facebook", "twitter", "band"}:
        return True
    if stripped in {"Q&A", "Q&A게시판", "하위메뉴", "대중교통안내", "교통안내", "자가운전", "POPUP", "상세보기", "지하철 이용"}:
        return True
    if any(
        token in stripped
        for token in [
            "전체 메뉴 닫기",
            "close전체",
            "home메인",
            "공유share",
            "pauseplay_arrow",
            "keyboard_arrow_left",
            "keyboard_arrow_right",
            "1일동안 열지않음",
            "Q&A게시판",
            "대중교통안내",
            "교통안내",
            "자가운전",
            "버스이용시 참고",
            "Total :",
            "한번에 보여질 게시물 갯수",
        ]
    ):
        return True
    if stripped.startswith("info "):
        return True
    if stripped.endswith("게시판") and len(stripped) <= 12:
        return True
    return False


def trim_lines_for_section(lines: list[str], dept_name: str, menu_labels: set[str], title_line: str) -> list[str]:
    trimmed: list[str] = []
    for line in lines:
        if line in STOP_MARKERS:
            break
        if should_skip_line(line, dept_name, menu_labels, title_line):
            continue
        trimmed.append(line)
    result: list[str] = []
    for line in trimmed:
        if result and result[-1] == line:
            continue
        result.append(line)
    return result


def choose_sections(initial_url: str, final_url: str, html: str) -> list[tuple[str, str]]:
    menu_items = parse_menu_items(html, final_url)
    chosen: list[tuple[str, str]] = []
    seen_urls: set[str] = set()
    parsed = urlparse(initial_url)
    if "/content/" in parsed.path:
        chosen.append(("학과개요", initial_url))
        seen_urls.add(initial_url)

    for canonical, keywords in SECTION_PATTERNS:
        for label, url in menu_items:
            if url in seen_urls:
                continue
            if any(keyword in label for keyword in keywords):
                if canonical == "졸업 후 진로" and ("취업광장" in label or "게시판" in label):
                    continue
                chosen.append((label, url))
                seen_urls.add(url)
                break

    if not chosen:
        chosen.append(("학과개요", final_url))
    return chosen[:5]


def collect_department_sections(
    session: requests.Session,
    department: Department,
    program_meta: dict[str, dict[str, str]],
) -> dict[str, object]:
    source_url = department.source_href if department.source_href.startswith("http") else urljoin(ADMISSIONS_BASE, department.source_href)
    root_response = fetch(session, source_url)
    root_url = root_response.url
    root_html = root_response.text
    section_defs = choose_sections(source_url, root_url, root_html)
    menu_labels = {label for label, _ in parse_menu_items(root_html, root_url)}

    sections: list[Section] = []
    office_lines: list[str] = []
    for label, url in section_defs:
        try:
            response = fetch(session, url)
        except Exception:
            continue
        lines = visible_lines(response.text)
        title_line = html_title(response.text)
        cleaned = trim_lines_for_section(lines, department.name, menu_labels, title_line)
        if not cleaned:
            continue

        bullets: list[str] = []
        for line in cleaned:
            if line.startswith("Tel") or "안산대학로 155" in line or "경기도 안산시 상록구" in line:
                office_lines.append(line)
                continue
            if "학과사무실" in label:
                if re.search(r"\d{2,4}[)\-. ]\d{3,4}", line) or "안산대학로 155" in line or "경기도 안산시 상록구" in line:
                    bullets.append(line)
                continue
            bullets.append(line)
            if len(bullets) >= 5:
                break

        if bullets:
            sections.append(Section(label=label, url=url, bullets=bullets))

    if not sections:
        fallback_lines = trim_lines_for_section(visible_lines(root_html), department.name, menu_labels, html_title(root_html))
        fallback_bullets = [line for line in fallback_lines if not line.startswith("Tel") and "안산대학로 155" not in line][:5]
        if fallback_bullets:
            sections.append(Section(label="학과개요", url=root_url, bullets=fallback_bullets))

    meta = program_meta.get(department.name, {})
    office = unique(office_lines)
    return {
        "department": department,
        "official_url": root_url,
        "duration": meta.get("duration", ""),
        "capacity": meta.get("capacity", ""),
        "sections": sections,
        "office": office,
    }


def build_school_overview(home_html: str, departments: list[Department]) -> str:
    lines = visible_lines(home_html)
    unique_lines = unique(lines)
    tracks = [line for line in unique_lines if line in {"수시1차", "수시2차", "정시", "외국인 유학생", "편입학", "산업체위탁", "전공심화", "대학안내", "입시도우미"}]
    quick_menu = [line for line in unique_lines if line in {"입학상담", "학과안내", "내신성적산출", "전년도", "입시결과", "홍보영상", "학과소개", "진로체험센터"}]
    phone = next((line for line in unique_lines if re.fullmatch(r"0\d{2}\.\d+\~?\d*", line)), "031.363.7700~1")
    address = "경기도 안산시 상록구 안산대학로 155(일동)"
    slogan = "진심이 키운다 안산대학교" if "진심이 키운다 안산대학교" in unique_lines else "안산대학교"

    dept_by_category: dict[str, list[str]] = {}
    for item in departments:
        dept_by_category.setdefault(item.category, []).append(item.name)

    parts = [
        "# 안산대학교 공식 학교 개요 보강",
        "",
        "공식 출처",
        f"- 입학안내 메인: {ADMISSIONS_HOME_URL}",
        f"- 학과/전공 안내: {DEPARTMENT_LIST_URL}",
        "- 2026학년도 신입생 모집요강",
        "- PDF: https://iphak.ansan.ac.kr/upload/ansan/iphak/preferences/2026/MOZIP/0822175301327.pdf",
        "",
        "## 학교 및 입학안내 기본 정보",
        f"- 학교명: 안산대학교",
        f"- 학교 슬로건/메인 문구: {slogan}",
        f"- 대표 입학상담 전화: {phone}",
        f"- 기본 캠퍼스 주소: {address}",
        "- 입학안내 메인에서는 카카오톡 상담, 캠퍼스 투어, 입시상담, 학과안내, 내신성적산출 메뉴를 제공함",
        "",
        "## 입학안내 메인에서 확인되는 주요 메뉴",
    ]
    parts.extend(f"- {track}" for track in tracks)
    parts.extend(
        [
            "",
            "## 빠르게 접근 가능한 안내 메뉴",
        ]
    )
    parts.extend(f"- {item}" for item in quick_menu)
    parts.extend(
        [
            "",
            "## 학과 및 전공 계열 구성",
        ]
    )
    for category in CATEGORY_ORDER:
        names = dept_by_category.get(category)
        if not names:
            continue
        parts.append(f"### {category}")
        parts.extend(f"- {name}" for name in names)

    parts.extend(
        [
            "",
            "## 챗봇 응답 시 활용 포인트",
            "- 안산대학교 입학안내는 수시1차, 수시2차, 정시, 외국인 유학생, 편입학, 산업체위탁, 전공심화 메뉴를 분리 제공함",
            "- 학과/전공 안내 페이지에서 각 계열별 학과 목록과 공식 학과 사이트 링크를 확인할 수 있음",
            "- 학과별 상세 설명은 각 학과 공식 사이트의 학과개요, 비전/교육목표, 교육과정, 졸업 후 진로, 학과사무실 페이지를 함께 참고하는 것이 좋음",
        ]
    )
    return "\n".join(parts) + "\n"


def build_department_markdown(records: list[dict[str, object]]) -> str:
    parts = [
        "# 안산대학교 학과 및 전공 소개 보강",
        "",
        "공식 출처",
        f"- 학과/전공 안내: {DEPARTMENT_LIST_URL}",
        "- 각 학과 공식 사이트 학과개요, 비전/교육목표, 교육과정, 졸업 후 진로, 학과사무실 페이지",
        "- 2026학년도 신입생 모집요강",
        "- PDF: https://iphak.ansan.ac.kr/upload/ansan/iphak/preferences/2026/MOZIP/0822175301327.pdf",
        "",
        "## 활용 기준",
        "- 아래 내용은 학과 공식 페이지와 입학안내 학과 링크를 바탕으로 정리한 보강 문서임",
        "- 챗봇은 학과의 성격, 교육목표, 진로, 학과사무실 문의처를 설명할 때 이 문서를 우선 참고할 수 있음",
        "- 모집요강에 있는 학제와 입학정원 정보는 확인 가능한 경우 함께 적었음",
        "",
    ]

    grouped: dict[str, list[dict[str, object]]] = {}
    for record in records:
        department = record["department"]
        assert isinstance(department, Department)
        grouped.setdefault(department.category, []).append(record)

    for category in CATEGORY_ORDER:
        items = grouped.get(category)
        if not items:
            continue
        parts.append(f"## {category}")
        parts.append("")
        for record in items:
            department = record["department"]
            assert isinstance(department, Department)
            official_url = str(record["official_url"])
            duration = str(record.get("duration", "") or "")
            capacity = str(record.get("capacity", "") or "")
            sections = record["sections"]
            office = record["office"]

            parts.append(f"### {department.name}")
            parts.append(f"- 공식 학과 페이지: {official_url}")
            if duration and capacity:
                parts.append(f"- 학제 및 입학정원 참고: {duration}, 입학정원 {capacity}")
            elif duration:
                parts.append(f"- 학제 참고: {duration}")

            for section in sections:
                assert isinstance(section, Section)
                parts.append(f"- {section.label}")
                for bullet in section.bullets:
                    parts.append(f"  - {bullet}")

            office_lines = list(office) if isinstance(office, list) else []
            if office_lines:
                parts.append("- 학과사무실 참고")
                for line in office_lines[:3]:
                    parts.append(f"  - {line}")
            parts.append("")

    return "\n".join(parts)


def main() -> None:
    DOCS_DIR.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    session.headers.update({"User-Agent": USER_AGENT})

    admissions_home = fetch(session, ADMISSIONS_HOME_URL).text
    department_page = fetch(session, DEPARTMENT_LIST_URL).text

    departments = parse_department_cards(department_page)
    program_meta = parse_program_meta()
    records = [collect_department_sections(session, department, program_meta) for department in departments]

    overview_path = DOCS_DIR / "안산대학교_공식_학교_개요_보강.md"
    department_path = DOCS_DIR / "안산대학교_학과전공_학과별_소개_보강.md"

    overview_path.write_text(build_school_overview(admissions_home, departments), encoding="utf-8")
    department_path.write_text(build_department_markdown(records), encoding="utf-8")

    print(f"Wrote {overview_path}")
    print(f"Wrote {department_path}")


if __name__ == "__main__":
    main()
