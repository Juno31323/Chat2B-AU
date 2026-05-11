# 논문-구현 정합성 체크리스트

이 파일은 논문에 쓴 내용과 실제 코드/실험이 일치하는지 확인하는 체크리스트입니다.

논문에는 멋있게 쓰여 있지만 실제 구현은 다르면 문제가 됩니다. 예를 들어 논문에 “BM25를 사용했다”고 쓰려면 실제 BM25 구현이 있어야 합니다.

## 작성 방법

- 논문에 들어갈 주장마다 실제 구현 여부를 확인합니다.
- 불일치하면 “논문을 수정할지” 또는 “구현을 수정할지”를 기록합니다.
- 아직 확인하지 못한 것은 `TBD`로 둡니다.

## 체크리스트

| 항목 | 논문 표현 | 실제 구현 | 상태 | 조치 |
|---|---|---|---|---|
| Dense retrieval | PostgreSQL + pgvector cosine search | `chunk_embeddings.embedding` 기준 | 부분 완료 | Supabase smoke test 필요 |
| Sparse retrieval | BM25 | `chunks.content` 기준 BM25 | 구현됨 | 실제 run 기록 필요 |
| Hybrid retrieval | RRF | BM25 rank + dense rank RRF | 구현됨 | metric 검증 필요 |
| Text-only RAG | OCR 미포함 | `configs/rag_text_only.yaml` | 부분 완료 | 재인덱싱 run 필요 |
| OCR-RAG | OCR plain text 사용 | config/구조 준비 | 미완료 | OCR corpus 생성 필요 |
| Layout-aware OCR-RAG | layout block/metadata 사용 | config/구조 준비 | 미완료 | layout chunk 생성 필요 |
| Refusal guard | 근거 부족 시 답변 거절 | `RefusalGuardService` | 구현됨 | OOD 평가 필요 |
| Generation model | gpt-5-mini, important gpt-5 | OpenAI provider 구현 | 구현됨 | 실제 API run 필요 |
| Prompt | grounded_qa_v1 | `PromptTemplates` | 구현됨 | prompt snapshot 저장 필요 |
| Token usage 기록 | input/output/total/cost 저장 | `chat_logs` generation columns | 부분 완료 | 실제 API usage 확인 필요 |
| 공지 단위 검색 평가 | chunk 검색 후 notice_id 기준 평가 | `run_compare_methods.py`, `evaluate_predictions.py` | 구현됨 | gold_notice_id 포함 질문셋 필요 |
| Source Accuracy | 답변 출처 제목/게시일/URL 일치 평가 | 기록 템플릿/metric column 준비 | 부분 완료 | 평가 라벨링 필요 |
| Hallucination Rate | 문서 밖 생성 비율 평가 | metric column 준비 | 부분 완료 | judge protocol 필요 |
| Field Accuracy | 날짜/장소/연락처/대상/표 QA 정확도 | metric column 준비 | 부분 완료 | 필드형 질문셋 필요 |
| OCR quality 평가 | 대표 이미지 OCR 보존율 평가 | `ocr_quality_template.csv`, `ocr_quality_results` | 부분 완료 | 파일럿 실행 필요 |

## 불일치 기록 템플릿

```markdown
## YYYY-MM-DD - 불일치 항목

### 논문에 쓴 표현
- TBD

### 실제 구현
- TBD

### 문제점
- TBD

### 해결 방향
- 논문 수정 / 구현 수정 / 실험 제외 중 선택: TBD
```
