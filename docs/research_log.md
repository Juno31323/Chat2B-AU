# 연구 작업 로그

이 파일은 프로젝트에서 어떤 작업을 했는지 날짜별로 남기는 연구 일지입니다.

논문 실험에서는 “언제, 어떤 데이터와 설정으로, 어떤 코드를 실행했는지”가 중요합니다. 기억에 의존하지 않도록 코드 변경, 데이터 수집, 인덱싱, 실험 실행, 오류 수정 내용을 간단히 기록합니다.

## 작성 방법

- 하루에 한 번 또는 중요한 변경이 있을 때 기록합니다.
- 실제 API key, DB password, Supabase secret, OpenAI key, Gemini key는 절대 적지 않습니다.
- Text-only, OCR-RAG, Layout-aware OCR-RAG 중 어떤 조건에 영향을 주는지 적습니다.
- 실험 결과 숫자는 `records/` 또는 `experiments/runs/`에 저장하고, 여기에는 요약만 적습니다.

## 기록 템플릿

```markdown
## YYYY-MM-DD - 작업 제목

### 작업 목적
- TBD

### 작업 내용
- TBD

### 관련 조건
- Text-only RAG: TBD
- OCR-RAG: TBD
- Layout-aware OCR-RAG: TBD

### 변경 파일
- `TBD`

### 실행 명령
```text
TBD
```

### 실행 결과
- 성공/실패: TBD
- 생성된 run_id: TBD
- 결과 파일 위치: TBD

### 남은 문제
- TBD
```

## 2026-05-08 - 논문 실험 기록 구조 정리

### 작업 목적
- 논문 실험에서 데이터, 설정, 인덱스, 실행 결과, 오류 분석을 추적할 수 있도록 기록 구조를 만든다.

### 작업 내용
- `docs/`, `records/`, `experiments/runs/` 기록 구조를 정리했다.
- 실제 실험값은 아직 채우지 않고 CSV 템플릿과 작성 방법만 추가했다.

### 관련 조건
- Text-only RAG: 기록상 별도 구분 필요
- OCR-RAG: 기록상 별도 구분 필요
- Layout-aware OCR-RAG: 기록상 별도 구분 필요

### 변경 파일
- `docs/*.md`
- `records/*.csv`
- `experiments/runs/README.md`

### 실행 결과
- 기록 템플릿 생성

### 남은 문제
- 실제 데이터 수집 후 `dataset_manifest_template.csv`를 복사해 실제 manifest를 작성해야 한다.
- 실제 실험 run 후 `experiments/runs/YYYYMMDD_method_name/`에 결과를 저장해야 한다.
