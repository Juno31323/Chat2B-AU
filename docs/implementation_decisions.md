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
