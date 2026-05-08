# baseline_before_fix

작성일: 2026-05-08

## 보존 상태

- Git 기준 HEAD: `02a84ca` (`Fix style.css del img`)
- 보존 브랜치: `baseline_before_fix`
- 주의: 현재 작업 트리에는 미커밋 변경이 존재한다. `baseline_before_fix` 브랜치는 HEAD 기준 포인터이며, 미커밋 변경 전체를 커밋으로 보존한 상태는 아니다.
- 이 문서는 OCR, pgvector, BM25, hybrid retrieval 구현 전의 Text-only RAG 기준 상태를 기록한다.

## 프로젝트 구조 요약

- `src/main/java/com/chat2b/admissions`: Spring Boot 진입점
- `src/main/java/com/chat2b/admissions/config`: 애플리케이션, Gemini, DataSource 설정
- `src/main/java/com/chat2b/admissions/controller`: 공개 채팅 API와 관리자 API
- `src/main/java/com/chat2b/admissions/service`: ingestion, chunking, embedding, retrieval orchestration, answer generation, rate limit
- `src/main/java/com/chat2b/admissions/repository`: JDBC 기반 문서, 청크, 로그, index metadata 저장/조회
- `src/main/java/com/chat2b/admissions/model`: API 응답/요청 및 검색 결과 모델
- `src/main/java/com/chat2b/admissions/support`: IP, token, vector, corpus hash 유틸
- `src/main/resources/admissions-docs`: Text-only Markdown 문서와 참고 PDF 원본
- `src/main/resources/static`: 데모 UI 및 외부 삽입용 위젯
- `src/test/java/com/chat2b/admissions`: 일부 단위 테스트

## 현재 Text-only RAG 흐름

1. 데이터 로딩
   - 기본 `APP_BOOTSTRAP_LOCATION`은 `classpath:admissions-docs/*.md`이다.
   - 따라서 현재 기준선은 Markdown 텍스트 문서만 인덱싱한다.
   - PDF 파서는 존재하지만 기본 bootstrap 패턴에는 PDF가 포함되지 않는다.

2. 문서 정규화
   - Markdown/TXT는 UTF-8로 읽는다.
   - `#` heading을 section 경계로 사용한다.
   - PDF는 PDFBox `PDFTextStripper`로 페이지별 plain text를 추출한다.

3. chunk 생성
   - `TextChunker`가 section별 텍스트를 공백/문단 기준으로 나눈다.
   - 현재 chunk size는 코드 상수 `700` 중심이며, long paragraph hard split은 `620`이다.
   - 설정값 `app.chunk-size`, `app.chunk-overlap`은 metadata 기록에는 쓰이지만 실제 chunker 로직에는 아직 연결되지 않았다.

4. embedding 생성
   - Gemini API key가 있으면 Gemini embedding을 사용한다.
   - Gemini embedding 실패 또는 API key 없음이면 hashed embedding으로 fallback한다.
   - query embedding도 현재 인덱스 embedding mode에 맞춰 Gemini 또는 hashed를 사용한다.

5. 저장 방식
   - `documents`, `document_chunks`, `chat_logs`, `index_metadata` 테이블을 사용한다.
   - embedding은 PostgreSQL vector 타입이 아니라 text 컬럼에 `[0.1,0.2,...]` 형태 문자열로 저장된다.
   - `index_metadata`에는 document_count, chunk_count, embedding_model, chunk_size, chunk_overlap, tokenizer, corpus_hash, index_version이 기록된다.

6. 검색 방식
   - DB에서 전체 chunk를 조회한 뒤 Java에서 cosine similarity를 계산한다.
   - 이후 lexical overlap을 보조 점수로 더해 rerank한다.
   - 실제 pgvector 인덱스 검색, BM25, hybrid retrieval은 아직 없다.

7. 답변 생성 방식
   - Gemini API key가 있으면 retrieved context를 Gemini에 전달해 답변을 생성한다.
   - Gemini가 없거나 생성 실패 시 `DemoAnswerComposer`가 관련 sentence를 추출해 데모 답변을 만든다.
   - Gemini system prompt에는 문서 근거가 없으면 확인 불가 및 입학처 문의를 안내하라는 지시가 있다.

8. 출처 표시 방식
   - `ChatService`가 document title, page number, section name, snippet, similarity score를 `SourceReference`로 반환한다.
   - 프론트엔드는 현재 source를 1개만 표시하고 hover/click preview 형태로 보여준다.

## 문제 파일 목록

- `src/main/resources/schema.sql`
  - `document_chunks.embedding`이 text 컬럼이다. pgvector 타입/인덱스가 없다.
  - corpus profile별 물리적 index 분리가 없다.

- `src/main/java/com/chat2b/admissions/repository/AdmissionsRepository.java`
  - `searchSimilarChunks`가 전체 chunk를 모두 읽어 Java에서 cosine similarity를 계산한다.
  - pgvector SQL 검색, BM25, hybrid retrieval이 없다.
  - `clearKnowledgeBase`가 단일 테이블 전체를 지우므로 OCR profile이 추가되면 기준선이 섞이거나 삭제될 위험이 있다.

- `src/main/java/com/chat2b/admissions/service/TextChunker.java`
  - chunk size/overlap이 설정값과 연결되어 있지 않다.
  - overlap이 구현되어 있지 않다.

- `src/main/java/com/chat2b/admissions/service/ChatService.java`
  - refusal guard가 검색 결과 없음 또는 Gemini prompt에 많이 의존한다.
  - lexical overlap 기반 match 조건이 넓어 관련 없는 문서가 match될 수 있다.
  - source는 API 응답에 score와 snippet이 포함되어 실험/운영 UI 정책을 더 분리할 필요가 있다.

- `src/main/java/com/chat2b/admissions/service/GeminiGateway.java`
  - prompt 일부 문자열이 콘솔/파일 표시상 mojibake로 보인다.
  - Gemini API 오류 메시지를 내부 예외로 던지고, 호출 실패 시 상위에서 fallback한다.

- `src/main/java/com/chat2b/admissions/support/TextTokenUtils.java`
  - 한국어 suffix 목록이 인코딩 문제처럼 보인다.
  - tokenization이 실험 재현성에 중요하므로 UTF-8/표현 안정화가 필요하다.

- `README.md`
  - 현재 표시상 mojibake가 있다.
  - README에는 pgvector 기반이라고 되어 있으나 실제 schema/search는 text embedding + Java cosine 방식이다.

## 수정 우선순위

1. 실험 profile 분리
   - `text_only`, `ocr_plain`, `ocr_layout`을 같은 테이블에 섞지 않도록 profile 컬럼 또는 별도 index namespace를 먼저 설계한다.

2. reindex 안전성
   - `clearKnowledgeBase`가 전체 삭제하지 않고 대상 profile/index_version만 삭제하도록 바꿔야 한다.

3. chunk 설정 연결
   - `app.chunk-size`, `app.chunk-overlap`, `app.tokenizer`가 실제 chunking/tokenizing 로직에 반영되어야 한다.

4. retrieval 기준선 명확화
   - 논문 기준선으로 hashed embedding을 쓸지, Gemini embedding을 쓸지 고정해야 한다.
   - pgvector/BM25/hybrid는 현재 미구현이므로 실험 조건에 포함하려면 별도 구현이 필요하다.

5. refusal guard 강화
   - similarity threshold, lexical overlap, evidence sufficiency를 분리해 문서 밖 질문에 대한 오답을 줄여야 한다.

6. encoding 정리
   - README, prompt, tokenizer suffix, 데모 답변 문자열의 UTF-8 표시 문제를 정리해야 한다.

## 다음 단계에서 고칠 항목

- 코드 변경 전 `baseline_before_fix` 문서와 git 상태를 기준으로 삼는다.
- 다음 작업은 OCR 구현이 아니라 profile/index 분리와 reindex 안전성부터 진행한다.
- Text-only baseline은 `APP_BOOTSTRAP_LOCATION=classpath:admissions-docs/*.md`, `APP_CORPUS_PROFILE=text_only`, `APP_INDEX_VERSION=text_only_v1` 기준으로 유지한다.
