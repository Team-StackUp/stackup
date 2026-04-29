# 환경 변수 및 환경 분리

> 모든 환경 의존 값은 환경변수로. 코드 하드코딩 금지. 신규 변수 추가 시 `.env.example` 동기화 필수.

---

## 1. 환경 분리

| 환경 | 용도 | 호스트 |
|------|------|--------|
| `local` | 개발자 로컬 (Docker Compose) | localhost |
| `dev` | 통합 개발 환경 | dev.stackup.io (TBD) |
| `staging` | 사전 배포 검증 | stg.stackup.io (TBD) |
| `prod` | 운영 | stackup.io (TBD) |

설정 우선순위 (낮음 → 높음):
1. 코드 default
2. `application.properties` / `pyproject.toml`
3. `application-{profile}.properties`
4. 환경변수 (always wins)
5. CLI argument

---

## 2. 공통 환경 변수

`.env.example` 기준 (루트):

```dotenv
# ===== Database =====
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=stackup
POSTGRES_USER=stackup
POSTGRES_PASSWORD=stackup

# ===== RabbitMQ =====
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_MANAGEMENT_PORT=15672
RABBITMQ_USER=stackup
RABBITMQ_PASSWORD=stackup

# ===== Object Storage (MinIO) =====
S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET=stackup
S3_REGION=us-east-1
S3_PATH_STYLE=true

MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_BUCKET=stackup
```

> Redis 미사용 ([`architecture.md §4.5`](./architecture.md)).

---

## 3. Core Server 환경 변수

```dotenv
# ===== App =====
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080

# ===== Auth =====
JWT_SECRET=                       # base64-encoded 32+ bytes (필수, default 없음)
JWT_ACCESS_TTL_SECONDS=900        # 15min
JWT_REFRESH_TTL_SECONDS=1209600   # 14d

ENCRYPTION_KEY=                   # AES-256 key, base64 32 bytes (필수)

# ===== GitHub OAuth =====
GITHUB_OAUTH_CLIENT_ID=
GITHUB_OAUTH_CLIENT_SECRET=
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback

# ===== AI Server =====
AI_SERVER_BASE_URL=http://ai:8000
```

---

## 4. AI Server 환경 변수

```dotenv
# ===== App =====
AI_SERVER_PORT=8000
AI_LOG_LEVEL=INFO

# ===== LLM =====
GEMINI_API_KEY=
OPENAI_API_KEY=                   # Whisper STT에도 사용
LLM_DEFAULT_PROVIDER=gemini       # gemini | openai
LLM_PRO_MODEL=gemini-3.1-pro
LLM_FLASH_MODEL=gemini-3.1-flash

# ===== Embedding =====
EMBEDDING_MODEL=text-embedding-004
EMBEDDING_DIM=768                 # 모델별 차원수 (DB 스키마와 일치 필수)

# ===== STT/TTS =====
STT_PROVIDER=whisper-api          # whisper-api | whisper-self-hosted
WHISPER_MODEL=whisper-1           # OpenAI Whisper API 모델
TTS_PROVIDER=                     # 결정 시 추가
```

---

## 5. Frontend 환경 변수

Vite 환경변수는 `VITE_` 접두 필수 (런타임에 노출됨).

`frontend/.env.local`:
```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_SSE_BASE_URL=http://localhost:8080
VITE_GITHUB_OAUTH_CLIENT_ID=        # public OK
VITE_SENTRY_DSN=                    # 옵션
```

> 비밀값(API key 등)은 절대 `VITE_*`에 두지 않는다. 빌드 결과물에 평문으로 들어간다.

---

## 6. RealTime Server 환경 변수

```dotenv
REALTIME_PORT=9090
CORE_SERVER_BASE_URL=http://core:8080
```

---

## 7. .env 파일 관리 룰

- `.env.example` (루트, frontend, ai 각각): **커밋 O** — 키 이름 + dummy 값
- `.env`, `.env.local`, `.env.production`: **커밋 X** — `.gitignore`로 차단
- 변경 시: 코드 + `.env.example` 같은 PR에 포함, README 업데이트

---

## 8. 비밀 관리 (운영)

| 환경 | 저장소 |
|------|--------|
| local | `.env.local` (개인 머신) |
| dev/staging | K8s Secret + sealed-secrets 또는 Doppler |
| prod | AWS Secrets Manager + IRSA |

운영자만 접근. CI는 OIDC를 통해 일시적으로 권한 획득.

---

## 9. 설정 검증

서비스 부팅 시점에 필수 환경변수 검증:

### Spring Boot
```java
@ConfigurationProperties(prefix = "app.security")
@Validated
public record SecurityProperties(
    @NotBlank String jwtSecret,
    @NotBlank String encryptionKey
) {}
```

### FastAPI
```python
class Settings(BaseSettings):
    GEMINI_API_KEY: str  # required
    LLM_PRO_MODEL: str = "gemini-3.1-pro"
    model_config = SettingsConfigDict(env_file=".env")
```

누락 시 부팅 실패 (조용한 실패 금지).

---

## 10. 개발 환경 부트스트랩

```bash
cp .env.example .env                    # 루트
cp frontend/.env.example frontend/.env.local
cp ai/.env.example ai/.env

docker compose up -d                    # PG, RabbitMQ, MinIO 기동
cd backend && ./gradlew bootRun
cd ai && uv run uvicorn ai_server.main:app --reload
cd frontend && npm install && npm run dev
```

상세는 `infra/CLAUDE.md` 참조.
