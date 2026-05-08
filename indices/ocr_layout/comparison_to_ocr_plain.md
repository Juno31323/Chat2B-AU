# OCR Plain vs Layout-aware OCR-RAG

## OCR-RAG (`ocr_plain`)

- OCR 결과의 `plain_text`를 하나의 일반 텍스트 문서처럼 사용한다.
- `blocks`, `bbox`, `reading_order`, `confidence`, `block_type`은 검색 chunk에 반영하지 않는다.
- 목적은 이미지 공지의 텍스트 포함 여부만 비교하는 기준선이다.

## Layout-aware OCR-RAG (`ocr_layout`)

- OCR `blocks`를 읽기 순서대로 정렬하고 block 단위 layout-aware chunk를 만든다.
- 각 chunk는 `block_type`, `bbox`, `page`, `reading_order`, `confidence`를 metadata로 가진다.
- 날짜, 전화번호, 이메일은 정규식으로 추출해 `extracted_dates`, `extracted_contacts`에 저장한다.
- 표 형태 텍스트는 가능한 경우 Markdown table 또는 key-value 형태로 변환한다.
- 낮은 OCR confidence chunk는 metadata로 추적해 검색/답변 단계에서 별도 표시할 수 있게 한다.

## 실험상 차이

- `ocr_plain`은 “이미지 공지의 텍스트가 추가되었을 때”의 효과를 본다.
- `ocr_layout`은 “텍스트의 위치, 구조, 종류, 읽기 순서가 추가되었을 때”의 효과를 본다.
- 두 조건 모두 Text-only와 동일한 embedding, chunk size, BM25, dense, hybrid RRF 조건을 유지한다.
