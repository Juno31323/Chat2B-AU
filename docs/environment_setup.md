# Environment Setup

이 문서는 로컬 `.env`, Render 환경변수, Supabase/PostgreSQL 설정을 구분하기 위한 기준 문서입니다.

## 원칙

- `.env`는 로컬 전용입니다.
- `.env`는 Git에 커밋하지 않습니다.
- Git에는 `.env.example`만 커밋합니다.
- Render에는 `.env` 파일을 올리지 않고 Dashboard Environment Variables 또는 `render.yaml`의 `sync: false` 항목으로 값을 넣습니다.
- API key, DB password, admin key는 README나 코드에 직접 쓰지 않습니다.

## 로컬 실행

PowerShell에서 프로젝트 루트로 이동합니다.

```powershell
cd C:\Chat2B\college-admissions-chatbot
.\scripts\load-env.ps1
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

`.env`의 따옴표는 `scripts/load-env.ps1`에서 제거됩니다.

예:

```env
SUPABASE_DB_PASSWORD="password-with-special!"
```

PowerShell process 환경변수에는 `password-with-special!`만 들어갑니다.

## Render 실행

Render에서는 따옴표를 붙이지 않고 원문 값을 입력합니다.

필수 secret:

- `OPENAI_API_KEY`
- `APP_ADMIN_KEY`
- DB password 또는 Render managed database 연결값

권장 generation 설정:

```env
GENERATION_PROVIDER=openai
GENERATION_MODEL=gpt-5-mini
GENERATION_IMPORTANT_MODEL=gpt-5
GENERATION_TEMPERATURE=0.0
GENERATION_MAX_OUTPUT_TOKENS=512
GENERATION_PROMPT_VERSION=grounded_qa_v1
GENERATION_ALLOW_MOCK_FALLBACK=false
```

## 모델 선택 규칙

- 일반 질문: `gpt-5-mini`
- 중요한 샘플 질문: `important=true`일 때 `gpt-5`
- 논문 실험에서는 세 조건 모두 같은 모델 선택 규칙을 사용해야 합니다.

## 주의

- 현재 OpenAI는 답변 생성 provider입니다.
- 현재 embedding은 Gemini key가 있으면 Gemini embedding, 없으면 hashed embedding fallback을 사용합니다.
- 최종 논문에서 dense retrieval을 실제 API embedding으로 설명하려면 embedding provider도 별도 고정해야 합니다.
