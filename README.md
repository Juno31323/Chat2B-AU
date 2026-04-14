# College Admissions Chatbot MVP

Spring Boot based RAG chatbot MVP for college admissions websites.

This project is designed for public admissions guidance scenarios such as:

- admissions FAQ support
- policy and schedule lookup from official documents
- lightweight web widget embedding on an existing school website

The current bundled sample dataset uses official Ansan University admissions documents.

## Overview

The chatbot indexes structured admissions documents, retrieves the most relevant chunks for a user question, and generates a grounded answer based on those results.

It is built for early-stage demos and pilot deployments where a college or department wants to validate whether an admissions chatbot can reduce repetitive inquiries.

## Features

- RAG pipeline built on Spring Boot
- document chunking and indexing from bundled Markdown files
- PostgreSQL + `pgvector` support for vector retrieval
- local H2 fallback for fast development
- Gemini-backed answer generation when `GEMINI_API_KEY` is configured
- deterministic local fallback mode when no model key is provided
- embeddable widget UI for integration into external websites
- admin endpoints for status and reindex
- basic public traffic rate limiting

## Tech Stack

- Java 17
- Spring Boot
- JDBC
- PostgreSQL + `pgvector`
- H2
- Vanilla JavaScript / HTML / CSS
- Gemini API

## How It Works

1. Admissions documents are loaded from `src/main/resources/admissions-docs/`.
2. Documents are split into smaller chunks and stored with embeddings.
3. A user question is embedded and matched against the stored chunks.
4. The top results are used as context for answer generation.
5. The UI shows a concise answer with a lightweight source hint.

## Running Locally

The default local setup uses an H2 file database so the app can start without Docker.

### 1. Set environment variables if needed

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:APP_ADMIN_KEY="your-admin-key"
```

### 2. Start the application

```powershell
.\gradlew.bat bootRun
```

### 3. Open the app

[http://localhost:8080](http://localhost:8080)

## Environment Variables

Common configuration values:

- `GEMINI_API_KEY`
- `APP_ADMIN_KEY`
- `PORT`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `DATABASE_DRIVER_CLASS_NAME`
- `APP_BOOTSTRAP_ON_STARTUP`
- `APP_BOOTSTRAP_IF_EMPTY_ONLY`
- `APP_RESPONSE_SOURCE_LIMIT`
- `APP_IP_MINUTE_LIMIT`
- `APP_IP_DAILY_LIMIT`
- `APP_SESSION_MINUTE_LIMIT`
- `APP_SESSION_DAILY_LIMIT`
- `APP_TRUST_FORWARD_HEADERS`
- `APP_TRUSTED_PROXY_ADDRESSES`

## Widget Embed

Add the widget script to another website:

```html
<script
  src="http://localhost:8080/widget.js"
  data-label="Admissions Chat"
  data-title="Admissions Assistant"
  data-position="right"
  data-width="420"
  data-height="680"></script>
```

Available widget options:

- `data-label`
- `data-title`
- `data-position`
- `data-width`
- `data-height`
- `data-open`
- `data-chat-url`

## Admin API

- `GET /api/admin/status`
- `POST /api/admin/reindex`

Requests must include the `X-Admin-Key` header when admin access is enabled.

## Deployment Notes

This project can be deployed as:

- a standalone web app
- an iframe-based chatbot page
- an embeddable widget served from a separate domain

The repository includes `render.yaml` for a simple first deployment on Render.

## Project Structure

```text
src/main/java/                        Spring Boot backend
src/main/resources/admissions-docs/  bundled admissions knowledge files
src/main/resources/static/           widget and web UI
src/test/java/                       tests
render.yaml                          Render deployment blueprint
```

## Current Sample Data

The bundled sample documents are based on official Ansan University admissions materials, including:

- admissions overview notes
- question-style FAQ notes
- schedule and registration summaries
- department and major information

## Future Improvements

- richer admin document management
- better evaluation datasets for admissions QA
- production-grade monitoring and abuse protection
- institution-specific theming and multi-tenant support

## License

This repository currently does not define a license.
