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
.\gradlew.bat test --console=plain --no-daemon
python -m py_compile scripts\run_compare_methods.py scripts\evaluate_predictions.py
python scripts\run_compare_methods.py --limit 1
python scripts\evaluate_predictions.py --predictions experiments\runs\20260511_091603_compare_methods\results.jsonl
```

### 실행 결과
- 성공/실패: 성공
- Java test 통과
- Python syntax check 통과
- 샘플 비교 run 3 rows 생성 확인
- 샘플 metric CSV 6 rows 생성 확인
- 검증용 샘플 run 파일은 Git 작업물에 남기지 않도록 정리함
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

## 2026-05-08 - 논문 우선 구조로 전환

### 작업 목적
- 학교 시연용 MVP가 아니라 논문 실험 재현성을 최우선 목표로 프로젝트 기준을 재정립한다.

### 작업 내용
- PostgreSQL canonical schema를 `documents`, `chunks`, `chunk_embeddings` 기반으로 정리했다.
- Spring Repository가 기존 `document_chunks`가 아니라 `chunks`와 `chunk_embeddings`를 사용하도록 변경했다.
- experiment profile에서 `schema.sql` 자동 실행을 끄고, V1 SQL을 수동 적용하는 구조로 변경했다.
- V2 migration은 legacy `document_chunks`를 만들지 않도록 no-op 처리했다.
- README를 논문 실험 시스템 기준으로 다시 작성했다.

### 관련 조건
- Text-only RAG: V1 schema 기준으로 재인덱싱 필요
- OCR-RAG: 별도 index_name으로 구축 예정
- Layout-aware OCR-RAG: 별도 index_name과 layout metadata 확장 예정

### 실행 결과
- `.\gradlew.bat test --console=plain --no-daemon`
- `python -m py_compile scripts\run_compare_methods.py`
- 결과: 통과

### 남은 문제
- Supabase SQL Editor에서 V1 schema 실행
- 실제 PostgreSQL pgvector smoke test
- Text-only index 재생성

## 2026-05-11 - 수정 논문 기준 코드/SQL 정합성 보강

### 작업 목적
- 수정된 논문 원고의 비교 조건, 메타데이터, 평가 지표가 실제 코드와 SQL에 반영되도록 정리한다.

### 작업 내용
- PostgreSQL/H2 schema에 공지 메타데이터, OCR/layout chunk metadata, 평가 결과 테이블을 추가했다.
- 기존 PostgreSQL DB 보강용 `V4__add_paper_evaluation_metadata.sql` migration을 추가했다.
- 검색 결과와 비교 스크립트에 `notice_id` 기반 평가 필드를 추가했다.
- Retrieval metric 계산 스크립트와 OCR 품질 기록 템플릿을 추가했다.

### 관련 조건
- Text-only RAG: notice_id 기준 baseline 평가 가능
- OCR-RAG: OCR plain chunk가 원본 notice_id를 유지해야 함
- Layout-aware OCR-RAG: layout block metadata와 notice_id를 함께 유지해야 함

### 변경 파일
- `src/main/resources/schema.sql`
- `src/main/resources/db/migration/postgres/V1__create_rag_experiment_schema.sql`
- `src/main/resources/db/migration/postgres/V4__add_paper_evaluation_metadata.sql`
- `src/main/java/com/chat2b/admissions/model/*`
- `src/main/java/com/chat2b/admissions/repository/AdmissionsRepository.java`
- `src/main/java/com/chat2b/admissions/service/Bm25SearchService.java`
- `src/main/java/com/chat2b/admissions/service/HybridRetrievalService.java`
- `scripts/run_compare_methods.py`
- `scripts/evaluate_predictions.py`
- `records/*.csv`
- `README.md`
- `docs/*.md`

### 실행 명령
```text
TBD
```

### 실행 결과
- 성공/실패: TBD

### 남은 문제
- Supabase/pgvector 실제 DB에서 V1 또는 V4 SQL 적용 후 smoke test가 필요하다.
- Generation 평가 지표와 필드 정확도는 gold label 또는 judge protocol 확정 후 계산해야 한다.
