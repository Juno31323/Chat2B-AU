# 실험 프로토콜

이 파일은 Text-only RAG, OCR-RAG, Layout-aware OCR-RAG를 같은 조건에서 비교하기 위한 실행 절차입니다.

핵심 원칙은 “비교하려는 것만 다르게 하고 나머지는 같게 유지”하는 것입니다. 이 논문에서는 데이터 조건만 다르게 하고, retrieval/generation 설정은 가능한 한 동일하게 유지합니다.

## 비교 조건

| 조건 | 포함 데이터 | 포함하면 안 되는 데이터 |
|---|---|---|
| Text-only RAG | 텍스트 공지, FAQ, 텍스트 안내문 | 이미지 OCR, PDF OCR, layout metadata |
| OCR-RAG | Text-only 데이터 + 이미지 OCR plain text | layout block, bbox, table metadata |
| Layout-aware OCR-RAG | Text-only 데이터 + OCR block/layout metadata | plain OCR 문서를 그대로 중복 삽입 |

## 동일하게 유지할 항목

- 질문 세트
- embedding model
- embedding dimension
- chunk size
- chunk overlap
- tokenizer
- BM25 top-k
- dense top-k
- final top-k
- hybrid fusion 방식
- refusal threshold
- generation provider
- generation model 선택 규칙
- prompt version
- temperature
- max output tokens

## 실험 실행 순서

1. 데이터 수집 manifest 작성
2. Text-only corpus 생성
3. OCR plain corpus 생성
4. OCR layout corpus 생성
5. 각 조건별 index 생성
6. 각 조건별 index metadata 저장
7. 동일 질문 세트로 세 조건 실행
8. prediction 결과 저장
9. metric 계산
10. error analysis 작성
11. 논문 표 작성

## 실행 결과 저장 위치

각 실험은 아래 구조로 저장합니다.

```text
experiments/runs/YYYYMMDD_method_name/
  config_snapshot.yaml
  index_metadata.csv
  questions.csv
  predictions.jsonl
  predictions.csv
  metrics.csv
  error_analysis.csv
  run_log.json
```

## 주의 사항

- 실제 secret 값은 기록하지 않습니다.
- 실험 중 config를 바꾸면 run_id를 새로 만듭니다.
- Text-only index에 OCR 데이터가 섞이면 해당 run은 폐기합니다.
- OCR-RAG와 Layout-aware OCR-RAG는 반드시 index_name으로 분리합니다.
- 검색은 chunk 단위로 수행하되, 논문 retrieval 평가는 `notice_id` 기준으로 중복 제거해 계산합니다.
- Retrieval metric은 `scripts/evaluate_predictions.py`로 Recall@1/3/5, MRR, nDCG@5를 먼저 계산합니다.
- Answer Accuracy, Faithfulness, Source Accuracy, Hallucination Rate, 날짜/장소/연락처/대상/표 정확도는 별도 라벨링 또는 LLM-as-judge 절차가 필요하므로 임의 숫자를 채우지 않습니다.
