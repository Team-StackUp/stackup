# StackUp Core Server

StackUp Core Server는 프론트엔드, GitHub, AI 서버, DB, 메시지 브로커 사이에서 인증과 핵심 도메인 흐름을 조정하는 Spring Boot API 서버입니다.

이 서버는 사용자 인증, 사용자 프로필, GitHub repository 연동, repository 분석 요청, 분석 결과 저장의 기준 소스가 됩니다. 프론트엔드는 Core Server가 발급한 우리 서비스 access token으로 API를 호출하고, GitHub access token이나 GitHub client secret은 직접 다루지 않습니다.

## Core Server 책임

Core Server의 주요 책임은 아래와 같습니다.

인증:

- `POST /api/auth/github`

  - GitHub 로그인 URL을 생성합니다.
  - CSRF 방지를 위한 `state`를 DB에 저장합니다.
  - PKCE 검증을 위한 `code_verifier`를 DB에 저장하고 `code_challenge`를 GitHub URL에 포함합니다.

- `GET /api/auth/github/callback`

  - 프론트가 전달한 `code`, `state`를 검증합니다.
  - `state`에 매칭되는 `code_verifier`를 조회하고 삭제합니다.
  - GitHub에 `code`를 보내 GitHub access token을 발급받습니다.
  - GitHub user/email API로 사용자 정보를 조회합니다.
  - GitHub access token을 암호화해 DB에 저장합니다.
  - 우리 서비스 access token과 refresh token을 발급합니다.

- `POST /api/auth/refresh`

  - `refresh_token` HttpOnly cookie를 검증합니다.
  - 기존 refresh token을 revoke하고 새 refresh token을 발급합니다.
  - 새 access token을 반환합니다.

- `DELETE /api/auth/logout`

  - refresh token을 revoke합니다.
  - refresh cookie를 만료시킵니다.

- `GET /api/users/me`
  - `Authorization: Bearer {accessToken}`으로 현재 사용자 프로필을 반환합니다.

GitHub 연동:

- GitHub OAuth `code`를 GitHub access token으로 교환합니다.
- GitHub `/user`, `/user/emails` API를 호출해 사용자 식별 정보를 가져옵니다.
- 사용자는 GitHub username이 아니라 GitHub numeric id 기준으로 식별합니다.
- GitHub access token은 프론트나 AI 서버로 직접 전달하지 않습니다.
- GitHub access token은 AES-GCM으로 암호화해 DB에 저장합니다.

Repository 분석:

- GitHub repository 후보 조회와 Core DB 등록 기준을 관리합니다.
- repository 소유자와 현재 인증 사용자가 일치하는지 검증합니다.
- 분석 요청 시 repository 상태를 변경하고 메시지 발행 기준을 관리합니다.
- AI 서버 callback을 받아 분석 결과를 DB에 반영하는 책임을 가집니다.

보안 경계:

- 프론트에는 우리 서비스 access token만 응답합니다.
- refresh token은 HttpOnly cookie로만 내려줍니다.
- GitHub client secret은 서버 환경변수에만 존재해야 합니다.
- GitHub access token raw 값은 응답, 로그, RabbitMQ payload에 노출하지 않습니다.
- 에러 응답에는 `code`, `message`, `traceId`, `timestamp`, `details`를 포함합니다.

## 인증 흐름

StackUp의 로그인은 GitHub OAuth 인증을 우리 서비스 인증 세션으로 변환하는 흐름입니다. GitHub OAuth App의 client secret과 GitHub access token은 Core Server 내부에서만 사용합니다.

1. 프론트는 로그인 시작 시 `POST /api/auth/github`를 호출합니다.
2. Core Server는 `state`, `code_verifier`, `code_challenge`를 생성합니다.
3. Core Server는 `state`, `code_verifier`를 DB에 저장하고, GitHub authorization URL을 프론트에 반환합니다.
4. 프론트는 반환받은 `authorizationUrl`로 브라우저를 이동시킵니다.
5. 사용자는 GitHub에서 권한을 승인합니다.
6. GitHub는 `GITHUB_OAUTH_REDIRECT_URI`로 `code`, `state`를 전달합니다.
7. 프론트는 callback URL에서 `code`, `state`를 읽고 Core Server callback API로 그대로 전달합니다.
8. Core Server는 `state`를 조회해 `code_verifier`를 가져온 뒤 해당 state를 삭제합니다.
9. Core Server는 GitHub에 `code`, `client_id`, `client_secret`, `redirect_uri`, `code_verifier`를 전달해 GitHub access token으로 교환합니다.
10. Core Server는 GitHub access token으로 GitHub `/user`, `/user/emails` API를 호출합니다.
11. Core Server는 GitHub numeric id 기준으로 사용자를 생성하거나 갱신합니다.
12. Core Server는 GitHub access token을 AES-GCM으로 암호화해 DB에 저장합니다.
13. Core Server는 우리 서비스 access token을 response body로 반환합니다.
14. Core Server는 refresh token raw 값은 cookie로 내려주고, DB에는 refresh token hash만 저장합니다.
15. 프론트는 이후 API 요청에 `Authorization: Bearer {accessToken}`을 붙입니다.

프론트 책임:

- `authorizationUrl`을 그대로 사용해야 합니다.
- GitHub callback에서 `code`, `state`를 둘 다 읽어야 합니다.
- Core Server callback API는 `GET /api/auth/github/callback?code=...&state=...` 형식으로 호출해야 합니다.
- `code`, `state`는 request body가 아니라 query parameter로 전달해야 합니다.
- Core Server callback 응답의 `accessToken`을 이후 API 요청의 Bearer token으로 사용해야 합니다.

Core Server 책임:

- OAuth `state`를 생성, 저장, 검증, 1회 사용 후 삭제합니다.
- PKCE `code_verifier`를 서버 DB에만 저장합니다.
- GitHub `client_secret`을 서버 환경변수로만 관리합니다.
- GitHub access token raw 값을 프론트 응답, 로그, 메시지 payload에 노출하지 않습니다.
- GitHub access token은 암호화해 DB에 저장합니다.
- 자체 access token과 refresh token을 발급합니다.

인증 후 사용자 조회:

```http
GET /api/users/me
Authorization: Bearer {accessToken}
```

로그인 성공 전이나 access token 없이 호출하면 `AUTH_INVALID_TOKEN`이 반환됩니다.

## Environment Variables

환경변수는 실행 환경에서 주입합니다. `.env.example`은 필요한 키와 값의 형식을 보여주는 템플릿입니다.

`.env` 파일은 로컬 개발이나 서버 수동 실행에서만 사용하고, git에 올리지 않습니다.

```bash
cp .env.example .env
```

필수 인증 변수:

```env
JWT_SECRET=replace-with-long-random-jwt-secret
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_TTL_SECONDS=1209600
ENCRYPTION_KEY=replace-with-base64-encoded-32-byte-key
```

GitHub OAuth 변수:

```env
GITHUB_OAUTH_CLIENT_ID=your-github-oauth-client-id
GITHUB_OAUTH_CLIENT_SECRET=your-github-oauth-client-secret
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback
```

`JWT_SECRET`:

- 우리 서비스 JWT access token 서명에 사용됩니다.
- Base64 형식일 필요는 없습니다.
- 충분히 긴 랜덤 문자열을 사용합니다.
- 이 값을 바꾸면 기존 access token은 모두 무효화됩니다.

```bash
openssl rand -base64 64
```

`ENCRYPTION_KEY`:

- GitHub access token을 DB에 암호화 저장할 때 사용됩니다.
- 반드시 Base64 decode 결과가 32바이트여야 합니다.
- 아래 명령으로 생성합니다.
- 이 값을 바꾸면 기존에 암호화 저장된 GitHub access token은 복호화할 수 없습니다.

```bash
openssl rand -base64 32
```

`GITHUB_OAUTH_REDIRECT_URI`:

- GitHub OAuth App의 `Authorization callback URL`과 정확히 같아야 합니다.
- authorize 단계와 token exchange 단계에서 같은 값이 사용됩니다.
- `http`/`https`, domain, port, path가 하나라도 다르면 GitHub OAuth가 실패합니다.
- 로컬 프론트 테스트:

```env
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback
```

- 배포 프론트 테스트:

```env
GITHUB_OAUTH_REDIRECT_URI=https://www.udangtang.site/auth/callback
```

인프라 변수:

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=stackup
POSTGRES_USER=stackup
POSTGRES_PASSWORD=stackup
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=stackup
RABBITMQ_PASSWORD=stackup
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=stackup
S3_REGION=us-east-1
S3_PATH_STYLE=true
```

배포 환경변수 처리 기준:

- root compose 또는 서버 환경변수에서 주입합니다.
- backend 내부에 `docker-compose.yml`을 두지 않습니다.
- backend `Dockerfile`은 이미지를 만드는 책임만 가집니다.
- 운영/배포 secret은 repository에 커밋하지 않습니다.
- `.env.example`에는 실제 secret 값을 넣지 않습니다.
- `GITHUB_OAUTH_CLIENT_SECRET`, `JWT_SECRET`, `ENCRYPTION_KEY`, DB password, S3 secret은 secret으로 취급합니다.
- GitHub client secret이 노출되면 GitHub OAuth App에서 즉시 새 secret으로 교체합니다.

## Docker

backend 디렉토리에는 Dockerfile만 둡니다. compose 구성은 repository root에서 관리합니다.

이미지 빌드:

```bash
docker build -t stackup-backend ./backend
```

컨테이너 실행 시 최소로 필요한 외부 의존성:

- PostgreSQL
- RabbitMQ
- S3 호환 스토리지 또는 MinIO
- GitHub OAuth App

백엔드 기본 포트:

```text
40000
```

Nginx reverse proxy를 사용할 경우, OAuth redirect URI에는 내부 포트 `40000`을 넣지 않습니다. 외부 공개 URL만 사용합니다.

예:

```text
외부 공개 URL: https://www.udangtang.site
내부 백엔드: http://127.0.0.1:40000
```
