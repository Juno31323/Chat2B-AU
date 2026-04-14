# 대학 입학처 챗봇 MVP

Spring Boot 기반의 RAG 챗봇 MVP 프로젝트입니다.

이 프로젝트는 대학 입학처 홈페이지에서 자주 반복되는 질문을 줄이고, 공식 모집요강과 안내 문서를 바탕으로 빠르게 답변하는 챗봇을 만드는 것을 목표로 합니다.

현재 저장소에는 안산대학교 입학 관련 공식 자료를 바탕으로 구성한 예시 데이터셋이 포함되어 있습니다.

## 프로젝트 개요

이 챗봇은 구조화된 입학 문서를 인덱싱한 뒤, 사용자의 질문과 가장 관련 있는 문서 조각을 검색하고, 그 결과를 바탕으로 근거 있는 답변을 생성합니다.

초기 데모, 파일럿 운영, 학교 또는 학과 단위의 입학 상담 자동화 검증에 적합하도록 설계되어 있습니다.

## 주요 기능

- Spring Boot 기반 RAG 파이프라인
- Markdown 문서 기반 청킹 및 인덱싱
- PostgreSQL + `pgvector` 기반 벡터 검색 지원
- 빠른 로컬 개발을 위한 H2 기본 실행 환경
- `GEMINI_API_KEY` 설정 시 Gemini 기반 답변 생성
- API 키가 없을 때 동작하는 로컬 데모 모드
- 외부 홈페이지에 삽입 가능한 위젯 UI
- 상태 확인 및 재인덱싱을 위한 관리자 API
- 공개형 챗봇을 위한 기본 Rate Limiting

## 기술 스택

- Java 17
- Spring Boot
- JDBC
- PostgreSQL + `pgvector`
- H2
- Vanilla JavaScript / HTML / CSS
- Gemini API

## 동작 방식

1. `src/main/resources/admissions-docs/` 아래의 입학 문서를 불러옵니다.
2. 문서를 작은 단위로 나누고 임베딩과 함께 저장합니다.
3. 사용자의 질문을 임베딩하여 관련성이 높은 문서 조각을 찾습니다.
4. 검색된 결과를 바탕으로 답변을 생성합니다.
5. UI에는 간결한 답변과 가벼운 근거 표시가 함께 노출됩니다.

## 로컬 실행 방법

기본 설정은 H2 파일 데이터베이스를 사용하므로 Docker 없이도 바로 실행할 수 있습니다.

### 1. 필요 시 환경변수 설정

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:APP_ADMIN_KEY="your-admin-key"
```

### 2. 애플리케이션 실행

```powershell
.\gradlew.bat bootRun
```

### 3. 브라우저에서 접속

[http://localhost:8080](http://localhost:8080)

## 주요 환경변수

자주 사용하는 설정값은 다음과 같습니다.

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

## 위젯 임베드

다른 홈페이지에 아래와 같은 방식으로 위젯을 삽입할 수 있습니다.

```html
<script
  src="http://localhost:8080/widget.js"
  data-label="입학 상담"
  data-title="입학 도우미"
  data-position="right"
  data-width="420"
  data-height="680"></script>
```

사용 가능한 옵션은 다음과 같습니다.

- `data-label`
- `data-title`
- `data-position`
- `data-width`
- `data-height`
- `data-open`
- `data-chat-url`

## 관리자 API

- `GET /api/admin/status`
- `POST /api/admin/reindex`

관리자 기능을 사용할 때는 `X-Admin-Key` 헤더를 함께 전달해야 합니다.

## 배포 안내

이 프로젝트는 다음과 같은 형태로 배포할 수 있습니다.

- 독립 실행형 웹 애플리케이션
- iframe 기반 챗봇 페이지
- 별도 도메인에서 서비스되는 임베드 위젯

저장소에는 Render 첫 배포를 위한 `render.yaml` 파일과 Docker 배포용 `Dockerfile`이 포함되어 있습니다.

## 디렉터리 구조

```text
src/main/java/                        Spring Boot 백엔드
src/main/resources/admissions-docs/  입학 문서 지식 데이터
src/main/resources/static/           위젯 및 웹 UI
src/test/java/                       테스트 코드
render.yaml                          Render 배포 설정
```

## 현재 예시 데이터

기본으로 포함된 문서는 안산대학교 입학 관련 공식 자료를 바탕으로 구성되어 있으며, 다음과 같은 내용을 포함합니다.

- 입학 개요 정리
- 질문형 FAQ 문서
- 전형 일정 및 등록 관련 정리
- 학과 및 전공 정보

## 향후 개선 방향

- 관리자 문서 관리 기능 고도화
- 입학 QA 평가용 테스트셋 강화
- 운영 환경용 모니터링 및 보안 강화
- 학교별 테마 적용 및 확장성 개선

## 라이선스

현재 이 저장소에는 별도의 라이선스가 포함되어 있지 않습니다.
