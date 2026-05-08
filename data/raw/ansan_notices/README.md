# 안산대학교 공지 원천 데이터

이 디렉터리는 안산대학교 공식 홈페이지와 입학처/학과 홈페이지 공지를 RAG 실험 전 단계에서 보존하기 위한 raw data 영역입니다.

## 구조

```text
data/raw/ansan_notices/
  html/              # 공지 상세 HTML 원본
  images/            # 본문 이미지 원본
  pdfs/              # 본문 PDF 원본
  files/             # 기타 첨부 원본
  manifest.jsonl     # 공지 단위 metadata
  failed_urls.jsonl  # 수집 실패 URL
  manifest_schema.json
```

## 수집 명령

```powershell
python scripts\collect_ansan_notices.py --max-notices 5 --delay 1.0
```

기본 설정은 입학처 게시판을 대상으로 하며, seed와 다른 호스트로 이동하는 상세 링크는 수집하지 않습니다. 본교 홈페이지나 학과 홈페이지는 `--seed`를 추가해서 별도로 수집합니다.

```powershell
python scripts\collect_ansan_notices.py --seed "https://www.ansan.ac.kr/www/board/11" --max-notices 10 --delay 1.0
```

## 주의

- 이 raw data는 Text-only index에 자동 반영되지 않습니다.
- 이미지/PDF OCR 결과는 이후 `data/processed/ocr_plain/`, `data/processed/ocr_layout/` 단계에서 별도로 생성합니다.
- 큰 첨부파일은 기본적으로 25MB를 넘으면 URL만 manifest에 남기고 다운로드는 건너뜁니다.
