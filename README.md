# 대학 입학처 챗봇 MVP

Spring Boot 기반의 대학 입학처 RAG 챗봇 MVP 프로젝트입니다.

이 프로젝트는 대학 입학처 홈페이지에서 반복되는 질문을 줄이고, 공식 모집요강, FAQ, 학과 안내 문서를 바탕으로 답변하는 챗봇을 만드는 것을 목표로 합니다. 현재 저장소에는 안산대학교 입학 관련 공개 자료를 바탕으로 구성한 예시 Text-only 데이터셋이 포함되어 있습니다.

## 프로젝트 개요

현재 구현은 Text-only RAG 기준선입니다. Markdown 입학 문서를 청크로 나누어 저장하고, 사용자의 질문과 문서 청크의 임베딩 유사도를 계산해 관련 문서를 찾은 뒤 답변을 생성합니다.

이 저장소는 향후 논문 실험을 위해 다음 조건을 비교할 수 있도록 확장할 예정입니다.

- `text_only`: 텍스트 공지, FAQ, 모집요강 기반 기준선
- `ocr_plain`: Text-only 데이터에 이미지 공지 OCR plain text를 추가한 방식
- `ocr_layout`: OCR 결과와 layout block, table/title/body/date/contact metadata를 반영한 방식

## 현재 구현된 주요 기능

- Spring Boot 기반 RAG API 서버
- Markdown 문서 기반 데이터 로딩, section 분리, chunk 생성
- Gemini API key 설정 시 Gemini embedding 및 Gemini 답변 생성
- Gemini API key가 없거나 embedding 생성에 실패할 때 사용할 수 있는 hashed embedding 데모 모드
- DB에 문서, 청크, 채팅 로그, 인덱스 메타데이터 저장
- PostgreSQL에서 pgvector 사용 가능 시 `pgvector-cosine` dense retrieval 사용
- H2 또는 pgvector 사용 불가 환경에서는 Java 코드의 `in-memory-cosine` fallback 사용
- chunk 단위 BM25 sparse retrieval
- RRF 기반 hybrid retrieval
- cosine similarity 결과에 lexical overlap을 더해 재정렬하는 간단한 lexical reranking
- 외부 홈페이지에 삽입 가능한 위젯 UI
- 상태 확인 및 재인덱싱을 위한 관리자 API
- 공개형 챗봇을 위한 기본 Rate Limiting

## 아직 구현되지 않은 기능

아래 기능은 현재 README에서 구현된 기능처럼 표현하지 않습니다.

- OCR 이미지 공지 수집 및 OCR-RAG 인덱싱
- layout-aware OCR metadata 기반 검색
- profile별 물리적 인덱스 분리

## 기술 스택

- Java 17
- Spring Boot
- JDBC
- H2 파일 데이터베이스
- PostgreSQL + pgvector 선택 지원
- Vanilla JavaScript / HTML / CSS
- Gemini API

참고: H2 로컬 실행에서는 `pgvector` 타입을 사용할 수 없으므로 embedding 문자열을 기반으로 Java에서 cosine similarity를 계산합니다. PostgreSQL에서 pgvector extension을 사용할 수 있으면 애플리케이션이 `embedding_vector vector(n)` 컬럼을 준비하고 SQL cosine distance 기반 top-k 검색을 사용합니다.

## 현재 Text-only RAG 동작 방식

1. `src/main/resources/admissions-docs/*.md` 문서를 불러옵니다.
2. Markdown heading을 기준으로 section을 나눕니다.
3. `TextChunker`가 section 텍스트를 문단 단위로 나누고 긴 문단은 hard split합니다.
4. Gemini API key가 있으면 Gemini embedding을 생성합니다.
5. Gemini API key가 없으면 hashed embedding을 생성합니다.
6. 문서와 청크, embedding 문자열을 DB에 저장합니다.
7. 사용자의 질문도 같은 방식으로 embedding합니다.
8. PostgreSQL pgvector가 활성화되어 있으면 DB에서 cosine distance 기반 top-k 검색을 수행합니다.
9. pgvector를 사용할 수 없으면 모든 청크를 DB에서 조회한 뒤 Java에서 cosine similarity를 계산합니다.
10. similarity threshold와 lexical overlap을 기준으로 후보 청크를 필터링하고 재정렬합니다.
11. Gemini가 설정되어 있으면 검색된 청크를 context로 전달해 답변을 생성합니다.
12. Gemini가 없거나 실패하면 데모용 sentence extraction 방식으로 답변합니다.
13. UI에는 답변과 1개의 간단한 근거 표시를 제공합니다.

BM25 sparse retrieval은 `/api/admin/bm25-search`에서 dense retrieval과 별도로 확인할 수 있습니다. RRF 기반 hybrid retrieval은 `/api/admin/hybrid-search`에서 확인할 수 있습니다.

## Text-only 기준선 실행

논문 실험의 Text-only 기준선은 `configs/rag_text_only.yaml`에 고정되어 있습니다. 이 조건은 Markdown 텍스트 공지, FAQ, 텍스트형 안내문만 포함하며 OCR 결과, PDF OCR 결과, layout-aware chunk는 포함하지 않습니다.

Text-only index namespace:

- `indices/text_only/`

PowerShell 실행 예시:

```powershell
$env:GEMINI_API_KEY=""
$env:APP_BOOTSTRAP_LOCATION="classpath:admissions-docs/*.md"
$env:APP_INDEX_NAME="text_only"
$env:APP_CORPUS_PROFILE="text_only"
$env:APP_INDEX_VERSION="text_only_v1"
$env:APP_EMBEDDING_DIMENSIONS="256"
$env:APP_CHUNK_SIZE="700"
$env:APP_CHUNK_OVERLAP="0"
$env:APP_TOKENIZER="unicode-letter-number-v1"
$env:APP_FUSION_METHOD="rrf"
$env:APP_BM25_TOP_K="50"
$env:APP_DENSE_TOP_K="50"
$env:APP_HYBRID_TOP_K="5"
$env:APP_RRF_K="60"
$env:APP_REFUSAL_MIN_DENSE_SCORE="0.22"
$env:APP_REFUSAL_MIN_TOKEN_OVERLAP="1"
$env:APP_REFUSAL_MIN_TOKEN_COVERAGE="0.30"
$env:APP_REFUSAL_EVIDENCE_TOP_K="40"

.\gradlew.bat bootRun --args="--force-reindex"
```

기준선 산출물:

- `indices/text_only/index_metadata.json`
- `indices/text_only/corpus_manifest.json`
- `indices/text_only/sample_questions.jsonl`
- `indices/text_only/sample_results.jsonl`

## 검색 방식 용어

현재 검색 방식의 정확한 명칭은 다음과 같습니다.

- PostgreSQL + pgvector 사용 가능 시: `pgvector-cosine`
- H2 또는 pgvector 사용 불가 시: `in-memory-cosine`
- 희소 검색: `bm25`
- 결합 검색: `hybrid-rrf`
- 보조 재정렬: `lexical overlap reranking`
- 챗봇 답변 생성 경로: `dense embedding similarity + lexical overlap reranking`
- 실험용 hybrid retrieval 경로: `bm25 + dense retrieval + rrf fusion`

Hybrid retrieval은 BM25 rank와 dense rank를 RRF로 결합합니다. BM25 score와 dense score는 스케일이 다르므로 단순 합산하지 않습니다. pgvector 여부는 각 실험 run의 `denseRetrievalMode` metadata를 기준으로 기록합니다.

## 인덱스 메타데이터

논문 실험 재현을 위해 reindex 시 다음 메타데이터를 기록합니다.

- `index_name`
- `corpus_profile`
- `index_version`
- `created_at`
- `document_count`
- `chunk_count`
- `embedding_model`
- `embedding_dim`
- `chunk_size`
- `chunk_overlap`
- `tokenizer`
- `corpus_hash`
- `retrieval_config_hash`
- `source_data_path`
- `bm25_index_metadata`
- `fusion_method`
- `rrf_k`

시작 시 기존 index가 있더라도 metadata가 없거나 다음 항목 중 하나가 바뀌면 자동으로 재인덱싱합니다.

- 문서 수 또는 문서 내용 hash
- chunk size 또는 chunk overlap
- embedding model 또는 embedding dimension
- tokenizer
- retrieval config hash
- source data path

강제 재인덱싱은 두 방식으로 실행할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args="--force-reindex"
```

또는 서버 실행 중 관리자 API를 사용할 수 있습니다.

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/admin/reindex?force=true" `
  -Headers @{ "X-Admin-Key" = $env:APP_ADMIN_KEY }
```

## Refusal Guard

문서 밖 질문은 답변하지 않도록 `RefusalGuardService`에서 다음 기준을 확인합니다.

- 검색 결과가 비어 있으면 거절합니다.
- top dense score가 `APP_REFUSAL_MIN_DENSE_SCORE`보다 낮으면 거절합니다.
- 질문 token과 검색된 title/content의 overlap, coverage가 부족하면 거절합니다.
- 날씨, 학번, 주가, 2030학년도, 존재하지 않는 장학금, 개인 연락처 같은 OOD cue가 있으면 더 엄격하게 거절합니다.
- 거절 문구는 `제공된 공지에서 확인되지 않습니다. 입학처에 직접 문의해 주세요.`로 통일합니다.
- 정상 답변에는 사용한 source title, posted_at, URL을 함께 포함합니다.

관련 설정:

- `APP_REFUSAL_MIN_DENSE_SCORE`
- `APP_REFUSAL_MIN_TOKEN_OVERLAP`
- `APP_REFUSAL_MIN_TOKEN_COVERAGE`
- `APP_REFUSAL_EVIDENCE_TOP_K`
- `APP_REFUSAL_OUT_OF_DOMAIN_CUES`

## 로컬 실행 방법

기본 설정은 H2 파일 데이터베이스를 사용하므로 Docker 없이도 바로 실행할 수 있습니다.

### 1. 필요 시 환경변수 설정

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:APP_ADMIN_KEY="your-admin-key"
```

Gemini API key 없이 실행하면 hashed embedding과 데모 답변 생성 방식으로 동작합니다.

### 2. Windows PowerShell UTF-8 설정

한글 로그나 API 응답이 PowerShell에서 깨져 보이면 실행 전에 아래 설정을 먼저 적용합니다.

```powershell
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001
```

Windows Terminal 또는 PowerShell 7을 사용하면 한글 출력이 더 안정적입니다. 프로젝트의 Markdown, 실험 JSON/JSONL, OCR 결과 파일은 UTF-8 기준으로 저장합니다. Python으로 JSON/JSONL을 저장할 때는 `encoding="utf-8"`과 `ensure_ascii=False`를 사용합니다.

### 3. 애플리케이션 실행

```powershell
.\gradlew.bat bootRun
```

### 4. 브라우저에서 접속

[http://localhost:8080](http://localhost:8080)

## 주요 환경변수

- `GEMINI_API_KEY`
- `APP_ADMIN_KEY`
- `PORT`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `DATABASE_DRIVER_CLASS_NAME`
- `APP_BOOTSTRAP_ON_STARTUP`
- `APP_BOOTSTRAP_IF_EMPTY_ONLY`
- `APP_BOOTSTRAP_LOCATION`
- `APP_CORPUS_PROFILE`
- `APP_INDEX_VERSION`
- `APP_TOKENIZER`
- `APP_CHUNK_SIZE`
- `APP_CHUNK_OVERLAP`
- `APP_EMBEDDING_DIMENSIONS`
- `APP_PGVECTOR_ENABLED`
- `APP_PGVECTOR_INDEX_ENABLED`
- `APP_FUSION_METHOD`
- `APP_HYBRID_TOP_K`
- `APP_BM25_TOP_K`
- `APP_DENSE_TOP_K`
- `APP_RRF_K`
- `APP_RETRIEVAL_TOP_K`
- `APP_MIN_SIMILARITY`
- `APP_MAX_CONTEXT_CHARS`
- `APP_RESPONSE_SOURCE_LIMIT`
- `APP_IP_MINUTE_LIMIT`
- `APP_IP_DAILY_LIMIT`
- `APP_SESSION_MINUTE_LIMIT`
- `APP_SESSION_DAILY_LIMIT`
- `APP_TRUST_FORWARD_HEADERS`
- `APP_TRUSTED_PROXY_ADDRESSES`

## 위젯 임베드

다른 홈페이지에 아래와 같은 방식으로 위젯을 삽입할 수 있습니다.

```html
<script
  src="http://localhost:8080/widget.js"
  data-label="입학 상담"
  data-title="입학 도우미"
  data-position="right"
  data-width="420"
  data-height="680"></script>
```

사용 가능한 옵션은 다음과 같습니다.

- `data-label`
- `data-title`
- `data-position`
- `data-width`
- `data-height`
- `data-open`
- `data-chat-url`

## 관리자 API

- `GET /api/admin/status`
- `POST /api/admin/reindex`

관리자 기능을 사용할 때는 `X-Admin-Key` 헤더를 함께 전달해야 합니다.

## 배포 안내

이 프로젝트는 다음과 같은 형태로 배포할 수 있습니다.

- 독립 실행형 웹 애플리케이션
- iframe 기반 챗봇 페이지
- 별도 도메인에서 서비스되는 임베드 위젯

저장소에는 Render 첫 배포를 위한 `render.yaml` 파일과 Docker 배포용 `Dockerfile`이 포함되어 있습니다.

## 디렉터리 구조

```text
src/main/java/                        Spring Boot 백엔드
src/main/resources/admissions-docs/  입학 문서 지식 데이터
src/main/resources/static/           위젯 및 웹 UI
src/test/java/                       테스트 코드
render.yaml                          Render 배포 설정
```

## 현재 예시 데이터

기본으로 포함된 문서는 안산대학교 입학 관련 공개 자료를 바탕으로 구성되어 있으며, 다음과 같은 내용을 포함합니다.

- 입학 개요 정리
- 질문형 FAQ 문서
- 전형 일정 및 등록 관련 정리
- 학과 및 전공 정보

## pgvector / BM25 / Hybrid Retrieval 구현 계획

논문 실험 수준으로 검색 방식을 올리려면 다음 순서로 migration하는 것을 권장합니다.

1. profile 분리
   - `text_only`, `ocr_plain`, `ocr_layout`을 같은 인덱스에 섞지 않도록 `corpus_profile` 또는 별도 index namespace를 적용합니다.

2. pgvector 운영 검증
   - PostgreSQL 환경에서 `CREATE EXTENSION vector` 권한을 확인합니다.
   - `document_chunks.embedding_vector vector(n)` 컬럼 생성 여부를 확인합니다.
   - HNSW index 생성 가능 여부를 확인합니다.
   - `/api/admin/status`의 `denseRetrievalMode`가 `pgvector-cosine`인지 기록합니다.

3. BM25 추가
   - 현재는 애플리케이션 인메모리 BM25 index로 chunk 단위 sparse retrieval을 제공합니다.
   - tokenizer, corpus hash, document/chunk count를 BM25 metadata로 기록합니다.

4. Hybrid retrieval
   - dense retrieval 결과와 BM25 결과를 각각 산출합니다.
   - RRF로 결합합니다.
   - 실험 metadata에 retrieval mode와 fusion parameter를 기록합니다.

## 향후 개선 방향

- profile별 index/reindex 안전성 강화
- pgvector 운영 환경 검증
- Hybrid retrieval 평가 및 튜닝
- OCR plain text corpus 추가
- layout-aware OCR metadata 추가
- 입학 QA 평가용 테스트셋 강화
- 운영 환경용 모니터링 및 보안 강화

## 라이선스

현재 이 저장소에는 별도의 라이선스가 포함되어 있지 않습니다.
## DB Profile Usage For Experiments

H2 is kept for local development and automated tests. PostgreSQL/Supabase is the target database for paper experiments that claim pgvector-based dense retrieval.

### Local H2

Use this when developing UI, prompt flow, or non-pgvector logic.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Role:

- Uses H2 file DB under `.data/`
- Does not use pgvector
- Dense retrieval falls back to Java in-memory cosine
- Suitable for quick local checks only

### PostgreSQL / Supabase Experiment

Set database variables without committing secret values.

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://..."
$env:SUPABASE_DB_USER="..."
$env:SUPABASE_DB_PASSWORD="..."
$env:APP_INDEX_NAME="text_only"
$env:APP_CORPUS_PROFILE="text_only"
$env:APP_INDEX_VERSION="text_only_v1_experiment"
$env:APP_FORCE_REINDEX="true"

.\gradlew.bat bootRun --args="--spring.profiles.active=experiment"
```

For `ocr_plain` or `ocr_layout`, change only the index/profile/bootstrap values after building their corpus:

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_plain/corpus/*.md"
$env:APP_INDEX_NAME="ocr_plain"
$env:APP_CORPUS_PROFILE="ocr_plain"
$env:APP_INDEX_VERSION="ocr_plain_v1"
```

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_layout/corpus/**/*.md"
$env:APP_INDEX_NAME="ocr_layout"
$env:APP_CORPUS_PROFILE="ocr_layout"
$env:APP_INDEX_VERSION="ocr_layout_v1"
```

Role:

- Uses PostgreSQL/Supabase
- Requires `create extension if not exists vector`
- Uses `document_chunks.embedding_vector vector(256)` for pgvector cosine top-k search
- Keeps `text_only`, `ocr_plain`, and `ocr_layout` separated by `index_name`, `corpus_profile`, and `index_version`

### Migration Files

PostgreSQL migration drafts are stored under:

```text
src/main/resources/db/migration/postgres/
```

Important files:

- `V1__create_rag_experiment_schema.sql`: normalized paper experiment schema draft with `documents`, `chunks`, `chunk_embeddings`, `index_metadata`, `retrieval_logs`
- `V2__align_runtime_pgvector_tables.sql`: runtime Spring JDBC schema alignment for `documents`, `document_chunks`, `embedding_vector`, and index separation

### PostgreSQL pgvector Smoke Test

The smoke test is skipped unless a PostgreSQL URL is configured.

```powershell
$env:EXPERIMENT_DATABASE_URL="jdbc:postgresql://..."
$env:DATABASE_USERNAME="..."
$env:DATABASE_PASSWORD="..."
.\gradlew.bat test --tests "*PostgresPgvectorSmokeTest"
```

It checks:

- DB connection
- `vector` extension
- Korean title/content insert and read
- sample vector insert
- cosine top-k search

## LLM Generation Settings For Paper Experiments

The paper comparison must keep generation fixed across `text_only`, `ocr_plain`, and `ocr_layout`.
Only the corpus/index condition should change.

Recommended default for paper experiments:

```text
GENERATION_PROVIDER=openai
GENERATION_MODEL=gpt-5-mini
GENERATION_IMPORTANT_MODEL=gpt-5
GENERATION_TEMPERATURE=0.0
GENERATION_MAX_OUTPUT_TOKENS=512
GENERATION_PROMPT_VERSION=grounded_qa_v1
GENERATION_ALLOW_MOCK_FALLBACK=false
GENERATION_INPUT_COST_PER_MILLION_TOKENS_USD=0.25
GENERATION_OUTPUT_COST_PER_MILLION_TOKENS_USD=2.00
GENERATION_IMPORTANT_INPUT_COST_PER_MILLION_TOKENS_USD=1.25
GENERATION_IMPORTANT_OUTPUT_COST_PER_MILLION_TOKENS_USD=10.00
OPENAI_API_KEY=...
```

Important sample questions can use `gpt-5` by sending `"important": true` in API/experiment question payloads.
Normal questions use `gpt-5-mini`.

OpenAI configuration:

```text
GENERATION_PROVIDER=openai
GENERATION_MODEL=gpt-5-mini
GENERATION_IMPORTANT_MODEL=gpt-5
OPENAI_API_KEY=...
GENERATION_INPUT_COST_PER_MILLION_TOKENS_USD=0.25
GENERATION_OUTPUT_COST_PER_MILLION_TOKENS_USD=2.00
GENERATION_IMPORTANT_INPUT_COST_PER_MILLION_TOKENS_USD=1.25
GENERATION_IMPORTANT_OUTPUT_COST_PER_MILLION_TOKENS_USD=10.00
```

Gemini can be selected with:

```text
GENERATION_PROVIDER=gemini
GENERATION_MODEL=gemini-2.5-flash-lite
GEMINI_API_KEY=...
GENERATION_INPUT_COST_PER_MILLION_TOKENS_USD=0.10
GENERATION_OUTPUT_COST_PER_MILLION_TOKENS_USD=0.40
```

`mock` / `demo-extractive` generation is for local development only. Do not use mock answers in final paper tables.
Every experiment run should record provider, model, model version, temperature, max output tokens, prompt version, run date, token usage, and estimated cost.
