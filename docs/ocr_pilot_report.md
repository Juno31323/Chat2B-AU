# OCR 파일럿 리포트

이 파일은 OCR 엔진을 최종 선택하기 전에 소량의 대표 이미지로 품질을 비교하는 보고서입니다.

OCR-RAG와 Layout-aware OCR-RAG의 성능은 OCR 품질에 크게 영향을 받습니다. 따라서 OCR 엔진을 바로 고정하지 않고, PaddleOCR/PP-Structure, CLOVA OCR, EasyOCR 등을 같은 샘플에서 비교합니다.

## 파일럿 샘플 구성

- 일반 포스터형 공지 3장
- 표 포함 공지 2장
- 날짜/마감일 포함 공지 2장
- 전화번호/이메일 포함 공지 1~2장
- 저화질 또는 복잡한 배경 공지 1~2장

## 비교 항목

- 한글 인식 정확도
- 날짜/마감일 인식 여부
- 전화번호/이메일 인식 여부
- 표 구조 보존 여부
- 제목/본문 구분 가능 여부
- confidence 제공 여부
- 실행 비용
- 실행 속도
- API key 필요 여부

## 작성 템플릿

```markdown
## YYYY-MM-DD - OCR 파일럿

### 사용한 샘플
- 샘플 수: TBD
- 이미지 유형: TBD

### 비교한 OCR 엔진
- PaddleOCR/PP-Structure: TBD
- CLOVA OCR: TBD
- EasyOCR/Tesseract: TBD

### 결과 요약
- 가장 좋은 엔진: TBD
- 선택 이유: TBD

### 발견한 문제
- TBD

### 다음 조치
- TBD
```

## 주의

- OCR 결과를 바로 Text-only index에 섞지 않습니다.
- OCR plain 결과와 layout-aware 결과는 별도 index로 관리합니다.
- OCR 결과 JSONL에는 engine_version, preprocess_profile, confidence를 기록합니다.
