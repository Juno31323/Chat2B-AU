# OCR 파일럿 출력

이 디렉터리는 OCR 엔진 비교 실험의 입력 목록과 출력 JSONL을 저장합니다.

## 샘플 목록

- `sample_images.jsonl`: 현재 raw image 중 실제 실행 가능한 샘플 목록
- `sample_images_template.jsonl`: 논문 파일럿용 10장 구성 템플릿

## 실행 예시

```powershell
python -m src.ocr.run_preprocess_batch --profile all --samples data/processed/ocr_outputs/sample_images.jsonl --log data/processed/ocr_outputs/preprocess_results.jsonl

python -m src.ocr.run_ocr_batch --engine paddle --samples data/processed/ocr_outputs/sample_images.jsonl --output data/processed/ocr_outputs/paddle_none_results.jsonl --preprocess-profile none
python -m src.ocr.run_ocr_batch --engine pp-structure --samples data/processed/ocr_outputs/sample_images.jsonl --output data/processed/ocr_outputs/pp_structure_all_profiles_results.jsonl --preprocess-profile all
python -m src.ocr.run_ocr_batch --engine easyocr --samples data/processed/ocr_outputs/sample_images.jsonl --output data/processed/ocr_outputs/easyocr_poster_safe_results.jsonl --preprocess-profile poster_safe
python -m src.ocr.run_ocr_batch --engine clova --samples data/processed/ocr_outputs/sample_images.jsonl --output data/processed/ocr_outputs/clova_document_scan_results.jsonl --preprocess-profile document_scan
```

전처리만 먼저 확인하려면 `run_preprocess_batch`를 사용합니다. OCR까지 실행하려면 각 OCR 엔진 의존성 또는 CLOVA API key가 필요합니다.

## 전처리 profile

- `none`: 원본 이미지 그대로 OCR한다. raw baseline이다.
- `basic`: EXIF 방향 보정 후 작은 이미지만 확대한다.
- `document_scan`: 스캔 문서/표 이미지용. 회색조, 자동 대비, 약한 denoise, 가능 시 deskew를 적용한다.
- `poster_safe`: 색상 포스터용. 컬러를 유지하고 resize와 약한 sharpen만 적용한다.
- `all`: 같은 샘플에 대해 모든 profile을 실행해 raw vs preprocessed 결과를 비교한다.

## 원칙

- OCR 결과는 여기 저장할 뿐, RAG index에 자동 반영하지 않습니다.
- raw 이미지와 preprocessed 이미지는 `preprocess_profile`로 구분합니다.
- 각 실행은 `.preprocess.jsonl` 로그를 함께 생성해 원본/전처리 크기, 적용 연산, warning을 남깁니다.
- 엔진 결정은 `ocr_comparison_report_template.md`를 채운 뒤 진행합니다.
