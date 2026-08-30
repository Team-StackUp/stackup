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
JWT_ACCESS_TTL_SECONDS=3600       # 1h (배포 기본; app.yml fallback 900). 면접 중 토큰 만료 방지용 상향
JWT_REFRESH_TTL_SECONDS=1209600   # 14d

ENCRYPTION_KEY=                   # AES-256 key, base64 32 bytes (필수)

# ===== GitHub OAuth =====
GITHUB_OAUTH_CLIENT_ID=
GITHUB_OAUTH_CLIENT_SECRET=
GITHUB_OAUTH_REDIRECT_URI=http://localhost:5173/auth/callback

# ===== Google OAuth (선택) =====
# 비워 두면 Google 로그인 버튼만 비활성되고 앱은 정상 기동한다.
# GitHub 쪽과 달리 @NotBlank 검증을 걸지 않은 이유가 이것 — 시크릿이 없는 채로
# 자동 배포가 돌면 애플리케이션 전체가 부팅에 실패하기 때문.
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
GOOGLE_OAUTH_REDIRECT_URI=http://localhost:5173/auth/google/callback

# ===== AI Server =====
AI_SERVER_BASE_URL=http://ai:8000
```

---

## 4. AI Server 환경 변수

```dotenv
# ===== App =====
AI_SERVER_PORT=8000
AI_LOG_LEVEL=INFO

# ===== LLM (Mindlogic CNU AC Gateway 단일 경로) =====
LLM_API_KEY=                      # 학교 발급 키
LLM_BASE_URL=https://factchat-cloud.mindlogic.ai/v1/gateway
LLM_PRO_MODEL=gemini-3.1-pro-preview
LLM_PRO_TEMPERATURE=0.2
LLM_PRO_TIMEOUT_SEC=30.0          # 요청 타임아웃(초). 미설정 시 SDK 기본값까지 무기한 대기 위험
LLM_FLASH_MODEL=gemini-3.5-flash-lite   # 꼬리질문(US-19) 저지연 모델
LLM_FLASH_TEMPERATURE=0.4
LLM_FLASH_MAX_TOKENS=512
LLM_FLASH_TIMEOUT_SEC=10.0        # Pro 보다 짧게 — 꼬리질문 저지연(<3s) 요구사항
QUESTIONS_RAG_TIMEOUT_SEC=1.5     # 질문 풀 생성 시 다문서 RAG 검색 상한. followup 과 대칭

# (외부 직접 호출용, fallback)
GEMINI_API_KEY=
OPENAI_API_KEY=                   # Whisper STT에도 사용

# ===== Object Storage =====
STORAGE_BACKEND=s3                # s3 | local
STORAGE_LOCAL_ROOT=./var/storage  # backend=local일 때만
S3_ENDPOINT_URL=http://localhost:38060
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin
S3_BUCKET_NAME=stackup
S3_REGION=us-east-1

# ===== Core 내부 API (github-token, embeddings upsert) =====
CORE_INTERNAL_BASE_URL=http://localhost:38010
CORE_INTERNAL_API_KEY=
CORE_INTERNAL_TIMEOUT_SEC=10

# ===== GitHub Repo 분석 =====
GITHUB_API_BASE_URL=https://api.github.com
GITHUB_FALLBACK_TOKEN=            # 사용자별 토큰 미발급 시 public 한정 호출 (옵션)
REPO_MAX_SOURCE_FILES=8
REPO_MAX_SOURCE_FILE_BYTES=50000
REPO_FETCH_TIMEOUT_SEC=30

# ===== Web 이력서 fetch =====
WEB_FETCH_TIMEOUT_SEC=20
WEB_MAX_HTML_BYTES=2000000

# ===== Embedding =====
EMBEDDING_PROVIDER=gemini         # mock | gemini | openai | ollama (개발/테스트는 mock 권장)
EMBEDDING_MODEL=gemini-embedding-001
EMBEDDING_DIM=1536                # DB 컬럼 차원과 일치 필수
EMBEDDING_CHUNK_SIZE=1000
EMBEDDING_CHUNK_OVERLAP=200
EMBEDDING_BATCH_SIZE=32            # 한 요청당 청크 수 (작을수록 429 회피, 호출 수↑)
FEEDBACK_COACHING_MAX_ANSWERS=30   # 답변별 복기 대상 상한 (초과분은 잘라내고 로그 남김)
FEEDBACK_COACHING_CONCURRENCY=5    # 복기 동시 호출 수 (429 회피용 — 낮출수록 느려지고 안전)
EMBEDDING_MAX_RETRIES=5           # 429(RESOURCE_EXHAUSTED) 지수 백오프 재시도 횟수
EMBEDDING_RETRY_BASE_DELAY_SEC=2.0  # delay = base*2^attempt + jitter (상한 30s)

# ===== Markdown 산출물 키 템플릿 =====
ANALYZED_RESUME_MD_KEY_TEMPLATE=analyzed/resume/{resume_id}/summary.md
ANALYZED_REPOSITORY_MD_KEY_TEMPLATE=analyzed/repository/{repository_id}/summary.md
ANALYZED_WEB_RESUME_MD_KEY_TEMPLATE=analyzed/web-resume/{resume_id}/summary.md
FEEDBACK_REPORT_MD_KEY_TEMPLATE=feedback/{session_id}/report.md

# ===== STT/TTS (Phase 2) =====
STT_PROVIDER=whisper-api          # whisper-api | whisper-self-hosted
WHISPER_MODEL=whisper-1           # OpenAI Whisper API 모델
TTS_PROVIDER=auto                 # auto | mock | openai | gemini | gateway (auto=LLM_API_KEY(gateway) > GEMINI_API_KEY > OPENAI_API_KEY)
OPENAI_TTS_MODEL=gpt-4o-mini-tts  # OpenAI TTS 모델 (질문 음성화)
OPENAI_TTS_VOICE=alloy            # TTS 보이스
OPENAI_TTS_TIMEOUT_SEC=30         # TTS HTTP 타임아웃(초)

# ===== 실시간 스트리밍 STT (RT3 음성 답변, Phase 2) =====
LIVE_STT_PROVIDER=auto                          # auto | mock | deepgram_live (auto=DEEPGRAM_API_KEY 보유 시 deepgram_live, 없으면 mock)
DEEPGRAM_API_KEY=                               # Deepgram streaming API 키 (live STT)
DEEPGRAM_LIVE_URL=wss://api.deepgram.com/v1/listen  # Deepgram Live WS 엔드포인트
DEEPGRAM_LIVE_MODEL=nova-2                       # 스트리밍 모델 (저지연, 한국어 지원)
DEEPGRAM_LIVE_LANGUAGE=ko                        # 인식 언어
DEEPGRAM_LIVE_ENDPOINTING_MS=800                 # 무음 N ms → utterance end
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

| 변수 | 기본값 | 용도 |
|------|--------|------|
| `REALTIME_LISTEN_ADDR` | `:8081` | HTTP 리슨 |
| `REALTIME_RABBITMQ_URL` | `amqp://stackup:stackup@localhost:5672/` | AMQP 연결 |
| `REALTIME_LOG_LEVEL` | `info` | slog 레벨 (debug/info/warn/error) |
| `REALTIME_QUEUE_NAME` | `q.realtime.session.notify` | 구독 큐 |
| `REALTIME_SSE_PING_INTERVAL` | `30s` | SSE heartbeat 주기 |
| `REALTIME_SSE_SLOW_CONSUMER_TIMEOUT` | `5s` | 구독자 send timeout |
| `REALTIME_SSE_BUFFER_SIZE` | `16` | 구독자별 채널 버퍼 |
| `REALTIME_AI_WS_URL` | `ws://localhost:8000/internal/voice/stream` | AI 음성 스트림 WS base URL (RT3 오디오 프록시 업스트림). compose 내부는 `ws://ai:8000/internal/voice/stream` |

루트 `.env.example`은 `REALTIME_PORT`, `REALTIME_LOG_LEVEL` 만 노출 (compose에서 매핑).

---

## 7. AI Server (메시징)

| 변수 | 기본값 | 용도 |
|------|--------|------|
| `RABBITMQ_URL` | `amqp://stackup:stackup@localhost:5672/` | AMQP 연결 |
| `AI_QUEUE_RESUME` | `ai.analyze.resume` | 이력서(PDF) 분석 큐 |
| `AI_QUEUE_REPOSITORY` | `ai.analyze.repository` | 레포 분석 큐 |
| `AI_QUEUE_WEB` | `ai.analyze.web` | 웹 이력서(URL) 분석 큐 |
| `AI_QUEUE_PREFETCH` | `10` | consumer prefetch |
| `AI_CALLBACK_EXCHANGE` | `stackup.ai-to-core` | 콜백 발행 exchange |
| `AI_CALLBACK_ROUTING_ANALYSIS` | `callback.analysis` | 콜백 routing key |
| `AI_PUBLISHER_NAME` | `ai-server` | envelope.publisher 값 |
| `AI_IDEMPOTENCY_LRU_SIZE` | `1024` | LRU 멱등 캐시 크기 |

---

## 8. .env 파일 관리 룰

- `.env.example` (루트, frontend, ai 각각): **커밋 O** — 키 이름 + dummy 값
- `.env`, `.env.local`, `.env.production`: **커밋 X** — `.gitignore`로 차단
- 변경 시: 코드 + `.env.example` 같은 PR에 포함, README 업데이트

---

## 9. 비밀 관리 (운영)

| 환경 | 저장소 |
|------|--------|
| local | `.env.local` (개인 머신) |
| dev/staging | K8s Secret + sealed-secrets 또는 Doppler |
| prod | AWS Secrets Manager + IRSA |

운영자만 접근. CI는 OIDC를 통해 일시적으로 권한 획득.

---

## 10. 설정 검증

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
    LLM_PRO_MODEL: str = "gemini-3.1-pro-preview"
    model_config = SettingsConfigDict(env_file=".env")
```

누락 시 부팅 실패 (조용한 실패 금지).

---

## 11. 개발 환경 부트스트랩

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

### 스토리지 고아 객체 회수 (Core)

```
STORAGE_ORPHAN_SWEEP_INTERVAL_MS=900000      # 스위퍼 주기 (기본 15분)
STORAGE_ORPHAN_SWEEP_INITIAL_DELAY_MS=60000  # 부팅 후 첫 실행 지연
```

`storage.orphan-sweep-interval-ms` / `storage.orphan-sweep-initial-delay-ms` 로 주입된다.
삭제된 자료의 S3 객체를 회수한다 (`docs/security.md §5.1.1`).

### 휘발성 레코드 보존 (Core)

```
MESSAGING_PROCESSED_MESSAGE_RETENTION_DAYS=30   # 멱등 레코드 보존 기간
MESSAGING_VOLATILE_SWEEP_INTERVAL_MS=86400000   # 멱등 레코드 정리 주기 (기본 24시간)
AUTH_REFRESH_TOKEN_SWEEP_INTERVAL_MS=86400000   # 만료 refresh token 정리 주기
OBSERVABILITY_AI_LOG_RETENTION_DAYS=90          # AI 호출 로그 보존 기간 (비용 추이 근거)
OBSERVABILITY_AI_LOG_SWEEP_INTERVAL_MS=86400000 # AI 호출 로그 정리 주기
```

보존 기간은 **재전달 창보다 길어야 한다** — 너무 짧으면 DLQ 에서 늦게 재주입된 메시지가
'처음 보는 메시지'가 되어 중복 처리된다(질문 중복·피드백 재생성).
