# 오류 분석 기록

이 파일은 챗봇이 틀린 답변을 했거나, 검색 결과가 잘못 나왔거나, 답변을 거절해야 하는데 답한 경우를 분석하는 문서입니다.

논문에서는 단순히 점수만 보여주는 것보다 “어떤 유형의 오류가 있었고 왜 발생했는지”를 설명하면 설득력이 좋아집니다.

## 오류 유형 예시

- retrieval_miss: 정답 문서를 검색하지 못함
- wrong_source: 관련 없는 문서를 근거로 사용함
- hallucination: 문서에 없는 내용을 생성함
- over_refusal: 답할 수 있는데 거절함
- under_refusal: 거절해야 하는데 답함
- ocr_error: OCR 텍스트가 잘못 인식됨
- layout_error: 제목/표/본문 구조 인식 실패
- stale_data: 오래된 문서가 사용됨
- ambiguous_question: 질문이 모호함

## 작성 방법

- 자세한 표 데이터는 `records/error_analysis_template.csv`를 복사해 작성합니다.
- 이 문서에는 오류 유형별 요약과 대표 사례를 적습니다.
- Text-only, OCR-RAG, Layout-aware OCR-RAG 중 어떤 조건에서 발생했는지 구분합니다.

## 요약 템플릿

```markdown
## YYYY-MM-DD - 오류 분석 요약

### 분석한 run_id
- TBD

### 가장 많이 발생한 오류 유형
- TBD

### 대표 오류 사례
- question_id: TBD
- method: TBD
- error_type: TBD
- 설명: TBD

### 개선 아이디어
- TBD
```
