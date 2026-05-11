# DB Migration Plan

## 현재 DB 구조

- 기본 profile 없음: `application.properties`는 H2 file DB를 기본값으로 사용한다.
- `local`: H2 file DB를 사용한다.
- `test`: H2 in-memory DB를 사용한다.
- `experiment`: PostgreSQL/Supabase 연결을 우선 사용한다.
- `production`: PostgreSQL/Supabase 연결을 우선 사용한다.

현재 애플리케이션은 JPA가 아니라 Spring JDBC를 사용한다. 초기 테이블은 `src/main/resources/schema.sql`로 생성된다.

## 현재 embedding 저장 방식

- H2/local fallback: `chunk_embeddings.embedding` text 컬럼에 vector literal 문자열을 저장한다.
- PostgreSQL + pgvector: `chunk_embeddings.embedding vector(256)` 컬럼을 사용하고 cosine distance top-k 검색에 사용한다.
- 현재 canonical schema는 `documents`, `chunks`, `chunk_embeddings`, `index_metadata`, `bm25_index_metadata`, `retrieval_logs`, `chat_logs`를 사용한다.
- 수정 논문 기준 평가를 위해 `experiment_runs`, `evaluation_questions`, `prediction_results`, `metric_results`, `error_analysis_results`, `ocr_quality_results`를 추가로 둔다.

## H2의 역할

- 개발용 DB: 사용 가능
- 단위 테스트용 DB: 사용 가능
- 데모용 DB: 소규모 데모에는 가능
- 논문 최종 실험 DB: 권장하지 않음

H2는 pgvector extension과 vector index를 제공하지 않으므로 논문에서 pgvector 기반 dense retrieval이라고 주장할 수 없다. H2 결과는 local smoke 또는 fallback 결과로만 분리 기록한다.

## PostgreSQL + pgvector 전환 필요성

논문 실험에서 dense vector retrieval을 주장하려면 PostgreSQL + pgvector 또는 이에 준하는 vector DB가 필요하다. 이 프로젝트는 Spring Boot와 PostgreSQL 배포 구조를 이미 사용하므로, 최종 실험 DB는 PostgreSQL + pgvector를 우선 권장한다.

Supabase는 PostgreSQL + pgvector를 제공하는 cloud-hosted option으로 적합하다. 단, extension 활성화 권한, HNSW index 생성 가능 여부, 무료 플랜 제한은 실제 프로젝트에서 확인해야 한다.

## 추천 DB 구조

- `documents`: 문서 단위 metadata
- `chunks`: 문서 chunk
- `chunk_embeddings`: chunk별 embedding vector
- `index_metadata`: corpus/index/model/chunk 설정과 corpus hash
- `bm25_index_metadata`: tokenizer와 BM25 index 생성 정보
- `retrieval_logs`: 질문별 dense retrieval 결과 기록
- `experiment_runs`: 실험 run 단위 metadata
- `evaluation_questions`: gold notice/answer가 있는 평가 질문
- `prediction_results`: 질문별 모델 답변과 검색 결과
- `metric_results`: Recall@k, MRR, nDCG@5, generation/field metric
- `error_analysis_results`: 오류 유형과 원인 분석
- `ocr_quality_results`: OCR 엔진/전처리별 품질 파일럿 결과

기준 SQL은 `src/main/resources/db/migration/postgres/V1__create_rag_experiment_schema.sql`에 둔다. 이미 예전 V1을 적용한 DB는 `V4__add_paper_evaluation_metadata.sql`을 추가 적용한다.

## 필요한 환경변수

- `SPRING_PROFILES_ACTIVE=experiment`
- `SUPABASE_DB_URL`
- `SUPABASE_DB_USER`
- `SUPABASE_DB_PASSWORD`
- `SUPABASE_PROJECT_URL`
- `GEMINI_API_KEY`
- `APP_ADMIN_KEY`
- `APP_CORPUS_PROFILE`
- `APP_INDEX_VERSION`
- `APP_PGVECTOR_ENABLED=true`
- `APP_PGVECTOR_INDEX_ENABLED=true`

실제 값은 `.env`, Render/Supabase dashboard, OS 환경변수에만 저장하고 코드나 문서에 쓰지 않는다.

## Migration 방식

현재 단계에서는 Flyway dependency를 추가하지 않고 명확한 SQL migration 초안을 보관한다. 다음 단계에서 Flyway를 도입할 경우 아래 순서를 권장한다.

1. `org.flywaydb:flyway-core`와 PostgreSQL 지원 dependency를 추가한다.
2. `spring.sql.init.mode=never`로 전환한다.
3. profile별 migration location을 분리한다.
4. H2 테스트 migration과 PostgreSQL 실험 migration을 분리한다.
5. 기존 H2 `.data`는 삭제하지 않고 `baseline_before_db_migration` 기준으로 보존한다.

## 위험 요소

- Supabase 무료 플랜의 connection 제한
- pgvector extension 권한 제한
- HNSW index 생성 실패 가능성
- embedding dimension 변경 시 vector column 재생성 필요
- H2 fallback 결과와 PostgreSQL experiment 결과가 섞일 위험
- `schema.sql`과 migration SQL을 동시에 운영할 때 schema drift가 생길 위험

## 지금 바로 전환 가능한가?

부분적으로 가능하다. 애플리케이션은 `experiment` profile에서 PostgreSQL/Supabase 연결을 받을 수 있고, pgvector가 가능하면 `pgvector-cosine`을 사용하도록 준비되어 있다.

다만 논문 최종 실험으로 바로 쓰기 전에는 다음 확인이 필요하다.

- Supabase 프로젝트에서 `create extension vector` 가능 여부
- `chunk_embeddings.embedding vector(256)` 생성 여부
- HNSW index 생성 여부
- `/api/admin/status`의 `denseRetrievalMode=pgvector-cosine` 확인
- `experiments/runs/YYYYMMDD_method_name/`에 run metadata 저장
