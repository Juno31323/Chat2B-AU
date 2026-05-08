# text_only Index Namespace

This directory stores metadata and smoke-test outputs for the Text-only RAG baseline.

Allowed data:
- Markdown text notices
- FAQ text
- Text-form admissions guides

Excluded data:
- Image OCR output
- PDF OCR output
- Layout-aware OCR chunks
- Table/title/body/date/contact layout metadata

Canonical config:
- `configs/rag_text_only.yaml`

Important rule:
- Keep this namespace stable. If corpus/config changes, rerun the whole `text_only` baseline and update metadata/results together.
