# StackUp Core Server

StackUp Core Server는 StackUp 서비스의 인증, 사용자, GitHub 연동, 분석 요청 중계를 담당하는 Spring Boot API 서버입니다.

Core Server는 GitHub OAuth 로그인 흐름을 처리하고, GitHub access token을 암호화해서 보관합니다. GitHub access token은 Core Server 내부에서만 사용하며 프론트엔드나 AI 서버로 전달하지 않습니다.

## 역할

- GitHub OAuth 로그인 URL 생성
- OAuth `state` 및 PKCE `code_verifier` 저장/검증
- GitHub OAuth `code`를 GitHub access token으로 교환
- GitHub 사용자 프로필/이메일 조회
- StackUp access token 발급
- refresh token 발급, 저장, 회전, 폐기
- 현재 사용자 프로필 조회
- AI 서버와 분석 요청/완료 callback 연동

## 인증 흐름

1. 프론트엔드가 `POST /api/auth/github`를 호출합니다.
2. Core Server가 `state`, `code_verifier`, `code_challenge`를 생성합니다.
3. Core Server가 `state -> code_verifier`를 DB에 저장하고 GitHub 로그인 URL을 응답합니다.
4. 프론트엔드는 응답의 `authorizationUrl`로 브라우저를 이동시킵니다.
5. 사용자가 GitHub에서 권한을 승인합니다.
6. GitHub가 `GITHUB_OAUTH_REDIRECT_URI`로 `code`, `state`를 전달합니다.
7. 프론트엔드는 callback URL에서 `code`, `state`를 읽어 Core Server callback API로 전달합니다.
8. Core Server는 `state`로 `code_verifier`를 조회하고 해당 state를 삭제합니다.
9. Core Server는 GitHub token API에 `code`, `client_id`, `client_secret`, `redirect_uri`, `code_verifier`를 전달합니다.
10. GitHub가 PKCE를 검증하고 GitHub access token을 발급합니다.
11. Core Server는 GitHub access token으로 `/user`, `/user/emails`를 조회합니다.
12. Core Server는 GitHub numeric id 기준으로 사용자를 생성하거나 갱신합니다.
13. Core Server는 StackUp access token을 response body로 반환합니다.
14. Core Server는 refresh token raw 값을 HttpOnly cookie로 내려주고, DB에는 refresh token hash만 저장합니다.
15. 프론트엔드는 이후 API 요청에 `Authorization: Bearer {accessToken}`을 붙입니다.

## 배포 포트

StackUp 서비스 포트는 아래 기준으로 고정합니다.

| 대상 | 포트 |
| --- | ---: |
| Nginx | 38000 |
| Vite Frontend | 38001 |
| Core Backend | 38010 |
| Realtime Server | 38020 |
| AI Server | 38030 |
| PostgreSQL | 38040 |
| RabbitMQ AMQP | 38050 |
| RabbitMQ Management | 38051 |
| MinIO API | 38060 |
| MinIO Console | 38061 |

Core Server의 내부 실행 포트는 `SERVER_PORT=38010`으로 맞춥니다. root compose, Nginx upstream, Dockerfile `EXPOSE`, `SERVER_PORT`는 모두 이 값을 기준으로 맞춰야 합니다.

요청 흐름 예시는 아래와 같습니다.

```text
브라우저
  -> Nginx:38000
  -> Core Backend:38010
  -> Spring Boot server.port=38010
```

## 환경변수 주입 방식

Spring Boot 설정은 [application.yml](src/main/resources/application.yml)의 `${...}` 표현식을 통해 환경변수에서 읽습니다.

예를 들어:

```yaml
server:
  port: ${SERVER_PORT:38010}
```

컨테이너 환경변수에 `SERVER_PORT=38010`이 들어가면 Spring Boot의 `server.port`가 `38010`이 됩니다. 값이 없으면 기본값 `38010`을 사용합니다.

`.env` 파일은 Spring Boot가 자동으로 읽지 않습니다. root `docker-compose.yml`에서 `env_file` 또는 `environment`로 컨테이너 환경변수에 주입해야 실제로 적용됩니다.

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

`JWT_SECRET`은 StackUp JWT access token 서명에 사용합니다. Base64 형식일 필요는 없고 충분히 긴 랜덤 문자열이면 됩니다.

생성 예시:

```bash
openssl rand -base64 64
```

`ENCRYPTION_KEY`는 GitHub access token 암호화에 사용합니다. Base64 decode 결과가 정확히 32바이트여야 합니다.

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

GitHub OAuth/API:

```env
GITHUB_OAUTH_CLIENT_ID=your-github-oauth-client-id
GITHUB_OAUTH_CLIENT_SECRET=your-github-oauth-client-secret
GITHUB_OAUTH_REDIRECT_URI=https://your-domain/auth/callback
GITHUB_OAUTH_BASE_URL=https://github.com
GITHUB_OAUTH_TOKEN_TYPE=bearer
GITHUB_OAUTH_CODE_CHALLENGE_METHOD=S256
GITHUB_API_BASE_URL=https://api.github.com
GITHUB_API_VERSION=2022-11-28
GITHUB_CONNECT_TIMEOUT=3s
GITHUB_READ_TIMEOUT=5s
```

`GITHUB_OAUTH_REDIRECT_URI`는 GitHub OAuth App의 `Authorization callback URL`과 정확히 같아야 합니다. `http`/`https`, domain, port, path 중 하나라도 다르면 GitHub OAuth가 실패합니다.

GitHub OAuth/프로필 조회는 사용자 로그인 흐름을 직접 막는 동기 요청이므로 timeout을 짧게 가져갑니다.

- `GITHUB_CONNECT_TIMEOUT=3s`: 연결 자체가 3초 이상 걸리면 네트워크 문제로 보고 빠르게 실패 처리
- `GITHUB_READ_TIMEOUT=5s`: OAuth/프로필 조회는 응답이 작으므로 5초 이상 지연되면 실패 처리

CORS:

```env
CORS_ALLOWED_ORIGINS=https://your-domain
```

프론트엔드가 여러 origin에서 접근해야 하면 comma-separated 값으로 주입합니다.

```env
CORS_ALLOWED_ORIGINS=https://your-domain,https://www.your-domain
```

인프라:

```env
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=stackup
POSTGRES_USER=stackup
POSTGRES_PASSWORD=stackup

RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=stackup
RABBITMQ_PASSWORD=stackup

S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=stackup
S3_REGION=us-east-1
S3_PATH_STYLE=true
```

외부에 노출되는 host port는 root compose에서 `38040`, `38050`, `38051`, `38060`, `38061`로 매핑합니다. 컨테이너 내부 통신은 서비스명과 내부 포트를 기준으로 맞춥니다.

## Docker

backend 디렉터리에는 Dockerfile만 둡니다. compose 구성은 repository root에서 관리합니다.

이미지 빌드:

```bash
docker build -t stackup-core-server ./backend
```

Dockerfile은 이미지를 만드는 책임만 가집니다. DB 주소, GitHub OAuth secret, 배포별 환경값은 root compose 또는 서버 환경변수로 전달합니다.

root compose 포트 예시:

```yaml
services:
  nginx:
    ports:
      - "38000:80"

  frontend:
    ports:
      - "38001:38001"

  backend:
    image: stackup-core-server
    environment:
      SERVER_PORT: 38010
    ports:
      - "38010:38010"

  realtime:
    ports:
      - "38020:38020"

  ai:
    ports:
      - "38030:38030"

  postgres:
    ports:
      - "38040:5432"

  rabbitmq:
    ports:
      - "38050:5672"
      - "38051:15672"

  minio:
    ports:
      - "38060:9000"
      - "38061:9001"
```

Nginx reverse proxy를 사용할 때 `GITHUB_OAUTH_REDIRECT_URI`에는 내부 backend port를 넣지 않습니다. 브라우저가 실제 접근하는 프론트엔드 callback URL을 넣습니다.

## 보안 기준

- GitHub client secret은 Core Server 환경변수로만 주입합니다.
- GitHub access token은 AES-GCM으로 암호화해서 DB에 저장합니다.
- GitHub access token은 Core Server 밖으로 전달하지 않습니다.
- refresh token raw 값은 DB에 저장하지 않고 SHA-256 hash만 저장합니다.
- refresh token raw 값은 HttpOnly cookie로만 전달합니다.
- GitHub client secret, GitHub access token, refresh token raw 값은 로그에 남기지 않습니다.
- AI 서버는 GitHub token을 직접 받지 않고, Core Server가 준비한 분석 입력 데이터 또는 S3 key를 사용합니다.

## 검증

전체 테스트:

```bash
./gradlew.bat test
```

배포 후 헬스 체크와 OAuth URL 발급 API는 Nginx 경유 주소로 확인합니다.

```bash
curl https://your-domain/api/system/health
curl -X POST https://your-domain/api/auth/github
```
