# Experiment Runs

이 디렉터리는 실제 실험 실행 결과를 저장하는 공간입니다.

각 실험은 별도 폴더로 저장합니다. 폴더 이름은 날짜, 방법, 모델을 알 수 있게 만듭니다.

## 권장 폴더명

```text
YYYYMMDD_method_model_short_note
```

예시:

```text
20260508_text_only_gpt5mini_baseline
20260508_ocr_plain_gpt5mini_paddleocr
20260508_ocr_layout_gpt5mini_ppstructure
```

## 권장 파일 구조

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
  README.md
```

## 저장 원칙

- 같은 질문 세트로 Text-only, OCR-RAG, Layout-aware OCR-RAG를 실행합니다.
- 실행 당시 config를 반드시 복사해 `config_snapshot.yaml`로 저장합니다.
- index metadata를 반드시 저장합니다.
- OpenAI key, Gemini key, DB password 같은 secret은 절대 저장하지 않습니다.
- 사람이 보기 좋은 분석표는 Google Sheets로 옮겨도 되지만, 원본 결과는 이 디렉터리에 남깁니다.
