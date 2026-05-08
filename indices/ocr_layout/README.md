# Layout-aware OCR-RAG Index

This index namespace is for the Layout-aware OCR-RAG condition.

- Includes: text notices, FAQ, text guides, OCR blocks with layout metadata
- Excludes: OCR plain-only documents
- Runtime index name: `ocr_layout`
- Runtime corpus profile: `ocr_layout`
- Runtime index version: `ocr_layout_v1`

Build corpus:

```powershell
python scripts\build_ocr_layout_corpus.py --ocr-results data/processed/ocr_outputs/ocr_results.jsonl
```

Run Spring reindex:

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_layout/corpus/**/*.md"
$env:APP_INDEX_NAME="ocr_layout"
$env:APP_CORPUS_PROFILE="ocr_layout"
$env:APP_INDEX_VERSION="ocr_layout_v1"
$env:APP_FORCE_REINDEX="true"
.\gradlew.bat bootRun
```
