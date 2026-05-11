# 구현 의사결정 기록

이 파일은 실험 시스템을 만들면서 내린 중요한 선택을 기록합니다.

논문에서는 “왜 이 방법을 선택했는가?”를 설명해야 합니다. 예를 들어 OCR 엔진, chunk size, embedding model, BM25 tokenizer, hybrid fusion 방식, refusal threshold를 선택한 이유를 남깁니다.

## 작성 방법

- 중요한 기술 선택을 할 때마다 새 항목을 추가합니다.
- 선택하지 않은 대안도 함께 적습니다.
- 나중에 논문 내용과 구현이 달라지면 `paper_alignment_checklist.md`에도 기록합니다.
- 비용이나 API key 같은 민감 정보는 적지 않습니다.

## 기록 템플릿

```markdown
## YYYY-MM-DD - 결정 주제

### 결정
- TBD

### 비교한 대안
- 대안 1: TBD
- 대안 2: TBD

### 선택 이유
- 정확도: TBD
- 재현성: TBD
- 비용: TBD
- 구현 난이도: TBD

### 영향을 받는 조건
- Text-only RAG: TBD
- OCR-RAG: TBD
- Layout-aware OCR-RAG: TBD

### 재검토 조건
- TBD
```

## 2026-05-08 - Generation model 기본값

### 결정
- 기본 답변 생성 모델은 `gpt-5-mini`로 둔다.
- 중요한 샘플 질문은 `important=true`로 표시하고 `gpt-5`를 사용한다.
- 세 실험 조건은 같은 모델 선택 규칙, 같은 prompt, 같은 temperature, 같은 max output tokens를 사용한다.

### 비교한 대안
- Gemini 2.5 Flash-Lite: 저비용 테스트에 유리하지만 현재 최종 생성 모델로는 선택하지 않음.
- Gemini 2.5 Flash: 품질 후보이나 이번 기본 설정에서는 제외.
- 모든 질문 GPT-5 사용: 품질은 높지만 비용 부담이 큼.

### 선택 이유
- 반복 실험 비용은 `gpt-5-mini`로 줄이고, 중요한 질의만 `gpt-5`로 검수할 수 있다.
- Text-only, OCR-RAG, Layout-aware OCR-RAG 모두 같은 규칙을 적용할 수 있다.

### 재검토 조건
- `gpt-5-mini` 답변 품질이 낮으면 전체 실험을 `gpt-5` 또는 최신 모델로 다시 수행한다.

## 2026-05-08 - 학교 시연 MVP에서 논문 실험 시스템으로 전환

### 결정
- 프로젝트의 1차 목표를 학교 시연용 위젯/데모가 아니라 논문 실험 재현성으로 변경한다.
- canonical DB schema는 `documents -> chunks -> chunk_embeddings` 구조로 둔다.
- 기존 `document_chunks` 중심 구조는 새 논문 실험 DB에서 사용하지 않는다.
- PostgreSQL/Supabase experiment profile에서는 `schema.sql` 자동 생성을 끄고 V1 SQL을 먼저 적용한다.

### 비교한 대안
- 빠른 데모 유지: `document_chunks` 기반으로 계속 개발
- 논문용 정규화 schema 전환: `chunks`, `chunk_embeddings`를 분리하고 metadata를 강화

### 선택 이유
- 논문에서 dense retrieval, BM25, hybrid retrieval을 설명하려면 chunk와 embedding을 명확히 분리하는 편이 좋다.
- Text-only, OCR-RAG, Layout-aware OCR-RAG를 `index_name`, `corpus_profile`, `index_version`으로 추적하기 쉽다.
- 나중에 OCR layout metadata를 chunk 단위로 붙이기 쉽다.

### 영향을 받는 조건
- Text-only RAG: V1 schema 기준으로 재인덱싱 필요
- OCR-RAG: 별도 index_name과 corpus_profile로 생성
- Layout-aware OCR-RAG: layout metadata 확장 시 chunks/chunk_embeddings 구조를 기준으로 확장

### 재검토 조건
- 실제 Supabase에서 V1 schema 실행이 실패하거나 pgvector 권한 문제가 생기는 경우
- embedding dimension을 256이 아닌 값으로 바꾸는 경우

## 2026-05-11 - 수정 논문 기준 평가 단위 정렬

### 결정
- 검색은 chunk 단위로 유지하되, 논문 지표는 `notice_id` 기준으로 계산한다.

### 비교한 대안
- chunk_id 기준 평가: 검색 구현과 직접 연결되지만 같은 공지가 여러 chunk로 중복 집계될 수 있음.
- document_id 기준 평가: 파일 단위 corpus에는 유용하지만 OCR/plain/layout corpus 간 ID가 달라질 수 있음.
- notice_id 기준 평가: 공식 공지 단위 비교에 가장 적합함.

### 선택 이유
- 수정 논문은 공지 단위의 Recall@k, MRR, nDCG@5 해석이 자연스럽다.
- Text-only, OCR-RAG, Layout-aware OCR-RAG가 서로 다른 chunk를 만들더라도 같은 공지를 맞혔는지 비교할 수 있다.

### 영향을 받는 조건
- Text-only RAG: `gold_notice_id`가 있는 질문셋으로 평가
- OCR-RAG: OCR chunk도 원본 공지의 `notice_id`를 유지
- Layout-aware OCR-RAG: layout chunk도 원본 공지의 `notice_id`를 유지

### 재검토 조건
- 공지 하나가 여러 독립 안내를 포함해 notice_id만으로 gold를 지정하기 어려운 경우
