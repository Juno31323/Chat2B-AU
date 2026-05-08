# OCR Plain RAG Index

This index namespace is for the OCR-RAG condition.

- Includes: text notices, FAQ, text guides, image OCR `plain_text`
- Excludes: OCR layout blocks, table structure, layout-aware chunks
- Runtime index name: `ocr_plain`
- Runtime corpus profile: `ocr_plain`
- Runtime index version: `ocr_plain_v1`

Build corpus:

```powershell
python scripts\build_ocr_plain_corpus.py --ocr-results data/processed/ocr_outputs/ocr_results.jsonl
```

Run Spring reindex:

```powershell
$env:APP_BOOTSTRAP_LOCATION="file:data/processed/ocr_plain/corpus/*.md"
$env:APP_INDEX_NAME="ocr_plain"
$env:APP_CORPUS_PROFILE="ocr_plain"
$env:APP_INDEX_VERSION="ocr_plain_v1"
$env:APP_FORCE_REINDEX="true"
.\gradlew.bat bootRun
```
