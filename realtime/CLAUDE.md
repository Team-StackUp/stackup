# RealTime Server — Claude 컨텍스트

> StackUp RealTime 서버. **Go 1.26 + chi + amqp091-go**. RabbitMQ consumer + SSE/WebSocket 서버. 본 문서 작성 시점에는 SSE만 활성, WS는 US-Session-03 / US-Voice-01에서 도입.

상위: [`/CLAUDE.md`](../CLAUDE.md) · 횡단 관심사: [`/docs/`](../docs/README.md)

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Go 1.26.3 (`go.mod`) |
| HTTP framework | `github.com/go-chi/chi/v5` |
| WebSocket | `github.com/coder/websocket` (현재 미사용, 모듈만 등록) |
| AMQP | `github.com/rabbitmq/amqp091-go` |
| Config | `github.com/caarlos0/env/v11` + godotenv |
| Logging | stdlib `log/slog` (JSON handler) |
| Validation | `github.com/go-playground/validator/v10` |
| 빌드 | `Makefile` |

---

## 2. 디렉토리 구조

```
realtime/
├── go.mod  go.sum  Makefile  Dockerfile  .env.example
├── cmd/
│   └── realtime/
│       └── main.go            조립 (config → 컴포넌트 → run)
└── internal/
    ├── config/                env-tag 기반 Config struct
    ├── transport/             chi 라우터 + 미들웨어 + SSE 핸들러
    ├── session/               sessionId → Subscriber 레지스트리
    ├── bridge/                MQ envelope 파싱 + 디스패처
    ├── messaging/             AMQP connection + reconnecting consumer
    └── trace/                 X-Trace-Id 미들웨어 + context helper
```

PDF의 Logical Architecture (Transport / Session State / Bridge / Messaging) 에 1:1 매핑.

---

## 3. 모듈 의존성 그래프

```
cmd/realtime ──→ transport ──→ session
              ├─→ bridge    ──→ session
              └─→ messaging ──→ (amqp091-go)

trace는 transport, cmd가 import
config는 cmd, internal/* 모두에서 import 가능
```

원칙:
- `internal/` 하위 패키지는 다른 프로젝트에서 import 불가 (Go 표준)
- `transport`는 도메인 로직(`session`, `bridge`)에 의존하되 역방향 금지
- `messaging`은 AMQP 라이브러리만 의존, 도메인 로직 모름

---

## 4. 책임 매트릭스

| 패키지 | 책임 |
|--------|------|
| `cmd/realtime` | 조립. config 로드, 컴포넌트 wiring, signal-aware shutdown |
| `internal/config` | 환경변수 → 타입 안전 Config |
| `internal/trace` | `X-Trace-Id` 추출/생성, context propagation |
| `internal/session` | sessionId → []*Subscriber. fan-out + slow consumer drop |
| `internal/transport` | chi 라우터, 미들웨어, `/health`, `/realtime/sessions/{id}` SSE |
| `internal/bridge` | RabbitMQ Envelope 파싱 + Dispatcher (sessionId 라우팅) |
| `internal/messaging` | AMQP connection + 무한 reconnect 가능한 consumer |

---

## 5. 비책임 (명시적)

- ❌ PostgreSQL 직접 접근 — 데이터가 필요하면 Core `/internal/*` API
- ❌ JWT 발급 — Core 책임. RealTime은 (도입 시) 서명 검증만
- ❌ 비즈니스 로직 (질문 생성, 분석) — AI 또는 Core
- ❌ RabbitMQ에 publish — Core를 통해서만 (`architecture.md §4.1`)

---

## 6. HTTP 엔드포인트

| ID | Method | Path | 책임 | 상태 |
|----|--------|------|------|------|
| - | GET | `/health` | 헬스체크 | 활성 |
| RT2 | GET | `/realtime/sessions/{id}` | SSE 작업 알림 | 활성 |
| RT1 | WS | `/realtime/sessions/{id}` | 라이브 면접 메시지 | **미구현** (US-Session-03) |
| RT3 | WS | `/realtime/sessions/{id}/audio` | 음성 스트림 | **미구현** (US-Voice-01) |

> RT1과 RT2는 동일 path. Upgrade 헤더 유무로 분기. 본 PR은 SSE만이라 분기 미적용.

---

## 7. RabbitMQ 토폴로지

| Queue | Bind | Consumer |
|-------|------|----------|
| `q.realtime.session.notify` | `stackup.realtime` exchange, routing key `realtime.session.*` | RealTime |

발행자: Core 서버. envelope 스키마는 [`/docs/messaging.md §5`](../docs/messaging.md).

---

## 8. SSE 와이어 포맷

```
id: <messageId>
event: <eventType>
data: {"data": <payload data>, "traceId": "<traceId>"}

```

Heartbeat (proxy keepalive):
```
: ping <unix-ts>

```

---

## 9. 동시성·세션 라우팅

- `session.Registry`는 sync.RWMutex로 보호.
- 한 sessionId에 여러 SSE 연결(다중 디바이스/탭) 가능 → slice.
- slow consumer 처리: `Dispatch`가 `slowTimeout`을 초과하면 그 구독자만 drop, 다른 구독자는 영향 없음.
- 단일 인스턴스 가정. 다중 인스턴스 전환 시 fanout exchange + 인스턴스별 자기 큐 패턴 필요.

---

## 10. 환경 변수

| 변수 | 기본값 | 용도 |
|------|--------|------|
| `REALTIME_LISTEN_ADDR` | `:8081` | HTTP 리슨 주소 |
| `REALTIME_RABBITMQ_URL` | `amqp://stackup:stackup@localhost:5672/` | AMQP 연결 |
| `REALTIME_LOG_LEVEL` | `info` | slog 레벨 |
| `REALTIME_QUEUE_NAME` | `q.realtime.session.notify` | 구독 큐 |
| `REALTIME_SSE_PING_INTERVAL` | `30s` | SSE heartbeat 주기 |
| `REALTIME_SSE_SLOW_CONSUMER_TIMEOUT` | `5s` | 구독자 send timeout |
| `REALTIME_SSE_BUFFER_SIZE` | `16` | 구독자별 채널 버퍼 |

---

## 11. 빌드·실행

```bash
make build           # bin/realtime
make run             # 호스트에서 실행
make test            # go test ./...
go test ./... -race  # 동시성 검증
docker build -t stackup-realtime ./realtime
```

`docker compose up -d realtime`은 루트 compose에 등록되어 있음.

---

## 12. 코드 스타일

- `gofmt` (Makefile `make fmt`)
- 패키지명은 단복수 단수
- 외부에 노출할 타입만 `PascalCase`, 내부는 `camelCase`
- `internal/` 활용으로 외부 import 차단
- 에러는 wrap (`fmt.Errorf("...: %w", err)`)

---

## 13. 안티패턴

- ❌ session.Registry 외부에서 `subs` 맵 직접 조작 → API만 사용
- ❌ goroutine 누수 → 모든 long-running goroutine은 `context.Context` 기반 종료
- ❌ Subscriber 채널을 두 번 close → Unsubscribe는 한 번만
- ❌ AMQP delivery에서 panic → recover middleware 또는 handler에서 catch + Nack
- ❌ blocking write to subscriber.Ch without timeout → 항상 select + slowTimeout

---

## 14. 신규 기능 추가 절차

새 SSE 이벤트 타입 추가:
1. `messaging.md §5`에 envelope 추가
2. Core 서버가 publish (별도 PR)
3. RealTime은 `bridge.Dispatch`가 자동 처리 (event_type만 다르고 라우팅 동일)

새 WebSocket 엔드포인트 (RT1/RT3):
1. `transport/ws_*.go` 추가
2. `coder/websocket`로 upgrade
3. `internal/session.Registry`를 protocol-agnostic으로 그대로 활용

새 환경 변수:
1. `internal/config/config.go`에 필드 + `env`/`envDefault` 태그
2. `realtime/.env.example` 갱신
3. 본 문서 §10 갱신

---

## 15. 현재 상태 (2026-05 기준)

- HTTP 서버 + `/health` 활성
- SSE `/realtime/sessions/{id}` 활성
- AMQP `q.realtime.session.notify` consumer 활성, dispatcher → SSE fan-out 동작
- WebSocket 미구현
- JWT 인증 미구현 (TODO 주석)
- DLQ 없음 (parse 에러 시 drop)
- Prometheus 노출 미구현

각 도입 시 본 문서 갱신.
