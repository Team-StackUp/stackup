# StackUp Core Server

StackUp Core Server는 프론트엔드, GitHub, AI 서버, PostgreSQL, RabbitMQ 사이에서 인증과 핵심 도메인 흐름을 담당하는 Spring Boot API 서버입니다.

프론트엔드는 Core Server가 발급한 StackUp access token으로 API를 호출합니다. GitHub client secret과 GitHub access token은 프론트엔드에서 직접 다루지 않습니다.

## Core Server 역할

인증:

- `POST /api/auth/github`
  - GitHub 로그인 URL을 생성합니다.
  - CSRF 방지용 `state`를 생성해 DB에 저장합니다.
  - PKCE 검증용 `code_verifier`를 DB에 저장하고, `code_challenge`를 GitHub URL에 포함합니다.

- `GET /api/auth/github/callback`
  - 프론트가 전달한 `code`, `state`를 검증합니다.
  - `state`로 저장된 `code_verifier`를 조회한 뒤 삭제합니다.
  - GitHub에 `code + code_verifier`를 보내 GitHub access token으로 교환합니다.
  - GitHub user/email API로 사용자 정보를 조회합니다.
  - GitHub access token을 암호화해 DB에 저장합니다.
  - StackUp access token과 refresh token을 발급합니다.

- `POST /api/auth/refresh`
  - HttpOnly cookie의 refresh token을 검증합니다.
  - 기존 refresh token을 revoke하고 새 refresh token으로 rotation합니다.
  - 새 StackUp access token을 응답합니다.

- `DELETE /api/auth/logout`
  - refresh token을 revoke합니다.
  - refresh cookie를 만료시킵니다.

- `GET /api/users/me`
  - `Authorization: Bearer {accessToken}`으로 현재 사용자 프로필을 반환합니다.

보안 경계:

- access token은 프론트에 응답 body로 전달합니다.
- refresh token은 HttpOnly cookie로만 전달합니다.
- refresh token 원문은 DB에 저장하지 않고 SHA-256 hash만 저장합니다.
- GitHub access token은 AES-GCM으로 암호화해 DB에 저장합니다.
- GitHub client secret, GitHub access token, refresh token 원문은 로그와 응답에 남기지 않습니다.

## 인증 흐름

1. 프론트가 로그인 시작 시 `POST /api/auth/github`를 호출합니다.
2. Core Server가 `state`, `code_verifier`, `code_challenge`를 생성합니다.
3. Core Server는 `state -> code_verifier`를 DB에 저장하고 GitHub authorization URL을 응답합니다.
4. 프론트는 응답의 `authorizationUrl`로 브라우저를 이동시킵니다.
5. 사용자가 GitHub에서 권한을 승인합니다.
6. GitHub가 `GITHUB_OAUTH_REDIRECT_URI`로 `code`, `state`를 전달합니다.
7. 프론트는 callback URL에서 `code`, `state`를 읽어 Core Server callback API로 전달합니다.
8. Core Server는 `state`로 `code_verifier`를 조회하고 해당 state를 삭제합니다.
9. Core Server는 GitHub token API에 `code`, `client_id`, `client_secret`, `redirect_uri`, `code_verifier`를 전달합니다.
10. GitHub가 PKCE 검증 후 GitHub access token을 발급합니다.
11. Core Server는 GitHub access token으로 `/user`, `/user/emails`를 조회합니다.
12. Core Server는 GitHub numeric id 기준으로 사용자를 생성하거나 갱신합니다.
13. Core Server는 StackUp access token을 response body로 반환합니다.
14. Core Server는 refresh token raw 값을 cookie로 내려주고, DB에는 refresh token hash만 저장합니다.
15. 프론트는 이후 API 요청에 `Authorization: Bearer {accessToken}`을 붙입니다.

## 환경변수 전달 방식

Spring Boot 설정은 [application.yml](src/main/resources/application.yml)에서 환경변수를 읽습니다.

예를 들어 [application.yml](src/main/resources/application.yml)에 아래처럼 정의되어 있습니다.

```yaml
server:
  port: ${SERVER_PORT:38010}
```

컨테이너 또는 Java 프로세스 환경변수에 `SERVER_PORT=38010`이 들어가면 Spring Boot의 `server.port` 값이 `38010`이 됩니다. 값이 없으면 기본값 `38010`을 사용합니다.

같은 방식으로 `JWT_SECRET`, `GITHUB_OAUTH_CLIENT_ID`, `POSTGRES_HOST` 같은 값도 [application.yml](src/main/resources/application.yml)의 `${...}` 표현식을 통해 환경변수에서 읽습니다.

로컬에서는 backend 디렉토리에 `.env`를 만들 수 있습니다.

```bash
cp .env.example .env
```

단, Spring Boot 애플리케이션이 `.env` 파일을 자동으로 읽는 것은 아닙니다. `.env` 파일의 값이 실제로 적용되려면 Docker Compose의 `env_file`, `environment`, shell `export`, IntelliJ Run Configuration, systemd 환경변수 같은 방식으로 프로세스 환경변수에 주입되어야 합니다.

서버 배포에서는 root `docker-compose.yml`에서 `env_file` 또는 `environment`로 값을 컨테이너에 전달하는 방식을 권장합니다. 실제 secret 값은 repository에 커밋하지 않습니다.

root compose 예시:

```yaml
services:
  backend:
    build:
      context: ./backend
    env_file:
      - ./backend/.env
    environment:
      SERVER_PORT: 38010
      POSTGRES_HOST: postgres
    ports:
      - "38010:38010"
```

위 설정의 의미:

- `env_file`은 `./backend/.env`의 값을 컨테이너 환경변수로 넣습니다.
- `environment`는 필요한 값을 직접 덮어씁니다.
- `SERVER_PORT=38010`은 Spring Boot 내부 실행 포트가 됩니다.
- `38010:38010`은 서버의 호스트 포트 `38010`을 컨테이너 내부 포트 `38010`으로 연결합니다.

포트 역할:

| 값 | 위치 | 의미 |
| --- | --- | --- |
| `SERVER_PORT=38010` | `.env`, compose `environment`, 서버 환경변수 | 컨테이너 내부 Spring Boot 실행 포트 |
| `38010:38010` | root `docker-compose.yml`의 `ports` | 호스트 `38010` 요청을 컨테이너 `38010`으로 전달 |
| `http://127.0.0.1:38010` | Nginx upstream | Nginx가 백엔드로 프록시할 주소 |
| `GITHUB_OAUTH_REDIRECT_URI` | GitHub OAuth App + 백엔드 환경변수 | 브라우저가 접근하는 프론트 callback URL |

## 필수 환경변수

인증:

```env
SERVER_PORT=38010
JWT_SECRET=replace-with-long-random-jwt-secret
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_TTL_SECONDS=1209600
JWT_ACCESS_TOKEN_TYPE=Bearer
ENCRYPTION_KEY=replace-with-base64-encoded-32-byte-key
```

`JWT_SECRET`:

- StackUp JWT access token 서명에 사용합니다.
- Base64 형식일 필요는 없습니다.
- 충분히 긴 랜덤 문자열을 사용합니다.

생성 예시:

```bash
openssl rand -base64 64
```

`ENCRYPTION_KEY`:

- GitHub access token 암호화에 사용합니다.
- 반드시 Base64 decode 결과가 32바이트여야 합니다.

생성 예시:

```bash
openssl rand -base64 32
```

OAuth/PKCE:

```env
OAUTH_STATE_TTL=5m
OAUTH_STATE_BYTE_LENGTH=32
PKCE_CODE_VERIFIER_BYTE_LENGTH=32
REFRESH_TOKEN_BYTE_LENGTH=32
```

Refresh token cookie:

```env
REFRESH_TOKEN_COOKIE_NAME=refresh_token
REFRESH_TOKEN_COOKIE_PATH=/api/auth
REFRESH_TOKEN_COOKIE_SAME_SITE=Strict
REFRESH_TOKEN_COOKIE_SECURE=true
REFRESH_TOKEN_COOKIE_HTTP_ONLY=true
```

GitHub OAuth:

```env
GITHUB_OAUTH_CLIENT_ID=your-github-oauth-client-id
GITHUB_OAUTH_CLIENT_SECRET=your-github-oauth-client-secret
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback
GITHUB_OAUTH_AUTHORIZATION_URL=https://github.com/login/oauth/authorize
GITHUB_OAUTH_TOKEN_URL=https://github.com/login/oauth/access_token
GITHUB_OAUTH_TOKEN_TYPE=bearer
GITHUB_OAUTH_CODE_CHALLENGE_METHOD=S256
GITHUB_API_BASE_URL=https://api.github.com
GITHUB_API_VERSION=2022-11-28
```

`GITHUB_OAUTH_REDIRECT_URI`는 GitHub OAuth App의 `Authorization callback URL`과 정확히 같아야 합니다. `http`/`https`, domain, port, path 중 하나라도 다르면 GitHub OAuth가 실패합니다.

로컬 프론트 테스트:

```env
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback
```

배포 프론트 테스트:

```env
GITHUB_OAUTH_REDIRECT_URI=https://www.udangtang.site/auth/callback
```

인프라:

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

## Docker

backend 디렉토리에는 Dockerfile만 둡니다. compose 구성은 repository root에서 관리합니다.

이미지 빌드:

```bash
docker build -t stackup-backend ./backend
```

Dockerfile은 이미지를 만드는 책임만 가집니다. 실제 DB 주소, GitHub OAuth secret, 배포별 환경값은 root compose 또는 서버 환경변수로 전달합니다.

포트 전달 예시:

```yaml
services:
  backend:
    image: stackup-backend
    environment:
      SERVER_PORT: 38010
    ports:
      - "38010:38010"
```

위 예시에서 `SERVER_PORT`는 컨테이너 내부의 Spring Boot 포트로 전달됩니다. `ports`의 왼쪽 값인 `38010`은 호스트 포트이고, 오른쪽 값인 `38010`은 컨테이너 내부 포트입니다.

요청 흐름:

```text
브라우저/외부 요청
  -> https://www.udangtang.site/api
  -> Nginx
  -> http://127.0.0.1:38010
  -> container:38010
  -> Spring Boot server.port=38010
```

`38010`은 Core Server의 확정 포트입니다. root compose, Nginx upstream, `SERVER_PORT`, Dockerfile `EXPOSE`를 모두 같은 값으로 맞춥니다.

Nginx reverse proxy를 사용할 때 `GITHUB_OAUTH_REDIRECT_URI`에는 내부 백엔드 API 포트를 넣지 않습니다. 브라우저가 실제 접근하는 프론트 callback URL을 넣습니다.

## 로컬 실행

로컬에서 backend만 실행하려면 `.env` 값을 현재 shell 환경변수로 올린 뒤 Spring Boot를 실행합니다.

PowerShell 예시:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#|^\s*$') { return }
  $pair = $_ -split '=', 2
  if ($pair.Length -eq 2) {
    Set-Item -Path "Env:$($pair[0].Trim())" -Value $pair[1].Trim()
  }
}

.\gradlew.bat bootRun
```

IntelliJ에서는 Run Configuration의 Environment variables에 `.env.example`의 키를 실제 값으로 넣습니다.

## 검증

전체 테스트:

```bash
./gradlew.bat test
```

로그인 URL 발급 확인:

```bash
curl -X POST http://localhost:38010/api/auth/github
```

배포 환경에서 Nginx를 거친 API 확인:

```bash
curl -X POST https://www.udangtang.site/api/auth/github
```

응답의 `authorizationUrl`에 들어간 `redirect_uri`가 GitHub OAuth App callback URL과 같은지 확인합니다.
