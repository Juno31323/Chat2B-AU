# 안산대학교 입학 공지 기반 RAG 실험 시스템

이 프로젝트는 학교 시연용 챗봇 MVP가 아니라, 논문 실험을 위한 RAG 비교 시스템입니다.

목표는 안산대학교 입학 관련 공개 자료를 대상으로 다음 세 조건을 비교하는 것입니다.

- `text_only`: 텍스트 공지, FAQ, 텍스트형 안내문만 사용하는 기준선
- `ocr_plain`: Text-only 데이터에 이미지 공지 OCR plain text를 추가한 조건
- `ocr_layout`: OCR 결과의 layout block, table/title/body/date/contact metadata를 반영한 조건

실험의 핵심은 데이터 조건만 다르게 하고, 검색 설정과 답변 생성 설정은 동일하게 유지하는 것입니다.

## 현재 기준

논문용 canonical DB schema는 다음 구조입니다.

```text
documents
chunks
chunk_embeddings
index_metadata
bm25_index_metadata
retrieval_logs
chat_logs
experiment_runs
evaluation_questions
prediction_results
metric_results
error_analysis_results
ocr_quality_results
```

기존 데모용 `document_chunks` 중심 구조는 더 이상 기준으로 사용하지 않습니다.

PostgreSQL/Supabase에서는 `src/main/resources/db/migration/postgres/V1__create_rag_experiment_schema.sql`이 기준 schema입니다. 이미 예전 V1을 적용한 DB는 `V4__add_paper_evaluation_metadata.sql`로 수정 논문용 메타데이터와 평가 테이블을 보강합니다.

## 구현 상태

구현됨:

- Spring Boot 기반 RAG API
- Text-only Markdown corpus 로딩
- chunk 생성
- PostgreSQL pgvector dense retrieval
- H2 local/test용 in-memory cosine fallback
- chunk 단위 BM25 sparse retrieval
- BM25 + dense RRF hybrid retrieval
- refusal guard
- OpenAI generation provider
- `gpt-5-mini` 기본 답변 생성
- `important=true` 질문의 `gpt-5` 사용
- index metadata 기록
- generation token/cost metadata 기록
- 논문 실험 기록 구조

준비됨:

- `ocr_plain` config
- `ocr_layout` config
- OCR adapter 구조
- OCR 전처리 구조
- 실험 비교 스크립트
- notice_id 기준 retrieval metric 계산 스크립트
- records CSV 템플릿

아직 실험 전 확인 필요:

- 실제 Supabase/PostgreSQL에서 V1 schema 적용
- 실제 pgvector smoke test
- 실제 embedding provider 고정
- OCR corpus 생성
- 평가 질문셋과 gold answer 작성

## 기술 스택

- Java 17
- Spring Boot 4
- Spring JDBC
- PostgreSQL / Supabase
- pgvector
- H2 for local/test
- OpenAI Responses API
- Gemini Embedding API 또는 hashed embedding fallback
- Python experiment scripts

## DB 전략

### Local/Test

로컬 개발과 테스트는 H2를 사용합니다.

- `schema.sql`은 H2 local/test 전용입니다.
- PostgreSQL의 `vector` type은 사용하지 않습니다.
- dense retrieval은 Java in-memory cosine fallback을 사용합니다.

실행:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

### Experiment

논문 실험은 PostgreSQL/Supabase + pgvector를 사용합니다.

- `application-experiment`에서는 `schema.sql` 자동 실행을 끕니다.
- Supabase SQL Editor 또는 PostgreSQL에서 V1 schema를 먼저 적용해야 합니다.
- 이미 V1을 적용한 기존 DB라면 V4도 추가로 적용합니다.
- `documents`, `chunks`, `chunk_embeddings`를 실제 런타임 테이블로 사용합니다.

적용할 SQL:

```text
src/main/resources/db/migration/postgres/V1__create_rag_experiment_schema.sql
src/main/resources/db/migration/postgres/V4__add_paper_evaluation_metadata.sql
```

V2는 deprecated no-op입니다. 새 논문 실험 DB에서는 `document_chunks`를 만들지 않습니다.

실행 예시:

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://..."
$env:SUPABASE_DB_USER="..."
$env:SUPABASE_DB_PASSWORD="..."
$env:OPENAI_API_KEY="..."
$env:APP_ADMIN_KEY="..."
$env:APP_INDEX_NAME="text_only"
$env:APP_CORPUS_PROFILE="text_only"
$env:APP_INDEX_VERSION="text_only_v1_experiment"

.\gradlew.bat bootRun --args="--spring.profiles.active=experiment"
```

강제 재인덱싱:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=experiment --force-reindex"
```

## 실험 조건 config

```text
configs/rag_text_only.yaml
configs/rag_ocr_plain.yaml
configs/rag_ocr_layout.yaml
```

세 config는 다음 항목을 동일한 기준으로 관리합니다.

- embedding model
- embedding dimension
- chunk size
- chunk overlap
- tokenizer
- BM25 top-k
- dense top-k
- final top-k
- fusion method
- refusal threshold
- generation provider
- generation model
- prompt version
- temperature
- max output tokens

## Generation model

기본 답변 생성:

```env
GENERATION_PROVIDER=openai
GENERATION_MODEL=gpt-5-mini
GENERATION_IMPORTANT_MODEL=gpt-5
GENERATION_TEMPERATURE=0.0
GENERATION_MAX_OUTPUT_TOKENS=512
GENERATION_PROMPT_VERSION=grounded_qa_v1
GENERATION_ALLOW_MOCK_FALLBACK=false
```

중요 질문만 `gpt-5`를 사용하려면 요청 또는 questions.jsonl에 `important=true`를 넣습니다.

```json
{
  "question": "수시 1차 원서접수 기간이 언제야?",
  "sessionId": "paper-run",
  "important": true
}
```

## Embedding

현재 embedding 흐름:

- `GEMINI_API_KEY`가 있으면 Gemini embedding 사용
- 없으면 hashed embedding fallback 사용

논문에서 실제 dense retrieval 성능을 주장하려면 embedding provider, embedding model, embedding dimension을 고정하고 `index_metadata`에 남겨야 합니다.

## Retrieval

Dense retrieval:

- PostgreSQL/Supabase: `chunk_embeddings.embedding <=> query_vector`
- H2 local/test: `chunk_embeddings.embedding` 문자열을 Java에서 cosine 계산

Sparse retrieval:

- `chunks.content` 기준 BM25

Hybrid retrieval:

- BM25 rank와 dense rank를 RRF로 결합
- score 단순 합산은 사용하지 않음

## Refusal guard

검색 근거가 부족하면 답변하지 않습니다.

기본 거절 문구:

```text
제공된 공지에서 확인되지 않습니다
```

## 실험 기록 구조

```text
docs/
  research_log.md
  implementation_decisions.md
  experiment_protocol.md
  paper_alignment_checklist.md
  error_analysis.md
  ocr_pilot_report.md

records/
  dataset_manifest_template.csv
  dataset_summary_template.csv
  system_config_template.csv
  index_metadata_template.csv
  evaluation_questions_template.csv
  run_log_template.csv
  prediction_results_template.csv
  metric_results_template.csv
  ocr_quality_template.csv
  error_analysis_template.csv
  paper_tables_template.csv

experiments/runs/
  README.md
```

Google Sheets는 분석과 공유용으로 사용할 수 있지만, 원본 실험 기록은 프로젝트 내부에 남깁니다.

## 권장 실험 순서

1. `records/dataset_manifest_template.csv`를 복사해 실제 dataset manifest 작성
2. Text-only corpus 확정
3. Supabase/PostgreSQL에 V1 schema 적용
4. `text_only` index 생성
5. `index_metadata` 저장 확인
6. 평가 질문셋 작성
7. Text-only run 실행
8. OCR pilot 수행
9. OCR plain corpus 생성
10. OCR-RAG run 실행
11. layout-aware OCR corpus 생성
12. Layout-aware OCR-RAG run 실행
13. `scripts/evaluate_predictions.py`로 notice_id 기준 retrieval metric 계산
14. error analysis 작성
15. paper table 근거 연결

## 주요 디렉터리

```text
src/main/java/                         Spring Boot backend
src/main/resources/admissions-docs/    Text-only source documents
src/main/resources/db/migration/        PostgreSQL experiment schema
configs/                               RAG condition configs
docs/                                  Research notes and protocols
records/                               CSV templates
experiments/runs/                      Experiment outputs
scripts/                               Utility scripts
src/ocr/                               OCR pilot modules
```

## 테스트

전체 테스트:

```powershell
.\gradlew.bat test --console=plain --no-daemon
```

PostgreSQL pgvector smoke test:

```powershell
$env:EXPERIMENT_DATABASE_URL="jdbc:postgresql://..."
$env:DATABASE_USERNAME="..."
$env:DATABASE_PASSWORD="..."
.\gradlew.bat test --tests "*PostgresPgvectorSmokeTest"
```

Python 스크립트 문법 확인:

```powershell
python -m py_compile scripts\run_compare_methods.py
python -m py_compile scripts\evaluate_predictions.py
```

세 조건 비교와 검색 지표 계산:

```powershell
python scripts\run_compare_methods.py --questions experiments\questions.jsonl
python scripts\evaluate_predictions.py --predictions experiments\runs\YYYYMMDD_HHMMSS_compare_methods\results.jsonl
```

## 보안

다음 값은 절대 Git에 커밋하지 않습니다.

- `.env`
- OpenAI API key
- Gemini API key
- Supabase DB password
- APP_ADMIN_KEY
- CLOVA OCR secret

Git에는 `.env.example`만 포함합니다.
