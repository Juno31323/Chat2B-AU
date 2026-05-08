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
| Dense retrieval | PostgreSQL + pgvector cosine search | TBD | TBD | TBD |
| Sparse retrieval | BM25 | TBD | TBD | TBD |
| Hybrid retrieval | RRF | TBD | TBD | TBD |
| Text-only RAG | OCR 미포함 | TBD | TBD | TBD |
| OCR-RAG | OCR plain text 사용 | TBD | TBD | TBD |
| Layout-aware OCR-RAG | layout block/metadata 사용 | TBD | TBD | TBD |
| Refusal guard | 근거 부족 시 답변 거절 | TBD | TBD | TBD |
| Generation model | gpt-5-mini, important gpt-5 | TBD | TBD | TBD |
| Prompt | grounded_qa_v1 | TBD | TBD | TBD |
| Token usage 기록 | input/output/total/cost 저장 | TBD | TBD | TBD |

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
