# 옵저버빌리티

> 분산 추적 + 구조화 로깅 + AI 비용·지연 모니터링.

---

## 1. 분산 추적 (X-Trace-Id)

### 1.1 ID 생성
- 클라이언트 또는 Nginx Gateway가 부여 (UUID v4)
- 한 요청의 모든 후속 처리(REST/Queue/SSE)에 동일 traceId 전파

### 1.2 전파 규약

| 경계 | 전파 방법 |
|------|-----------|
| HTTP | `X-Trace-Id` 헤더 |
| RabbitMQ | AMQP header `x-trace-id` |
| SSE | event payload `traceId` 필드 |
| Internal log | MDC (Spring) / contextvars (Python) |

### 1.3 Spring Boot 구현
```java
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            res.setHeader("X-Trace-Id", traceId);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

### 1.4 FastAPI 구현
```python
from contextvars import ContextVar
trace_id_var: ContextVar[str] = ContextVar("trace_id")

@app.middleware("http")
async def trace_middleware(request, call_next):
    trace_id = request.headers.get("x-trace-id", str(uuid4()))
    trace_id_var.set(trace_id)
    response = await call_next(request)
    response.headers["X-Trace-Id"] = trace_id
    return response
```

---

## 2. 로깅

### 2.1 출력 포맷 (JSON)
```json
{
  "ts": "2026-04-27T15:00:00.123Z",
  "level": "INFO",
  "service": "core-server",
  "traceId": "9f4e5b...",
  "userId": 42,
  "logger": "c.s.s.session.SessionService",
  "msg": "session created",
  "sessionId": 99
}
```

### 2.2 레벨 정책

| Level | 용도 | 예시 |
|-------|------|------|
| `ERROR` | 사용자 영향 + 운영자 조치 필요 | RabbitMQ 발행 실패, 외부 API 5xx |
| `WARN` | 회복 가능, 주시 필요 | retry 발생, fallback 발동, slow query |
| `INFO` | 도메인 이벤트 (감사 가능) | 회원가입, 세션 생성/종료, 분석 완료 |
| `DEBUG` | 개발용 (운영 OFF) | 메서드 진입, 파라미터 |
| `TRACE` | 트레이스 상세 | (거의 사용 X) |

### 2.3 로거별 권장 레벨
- 운영: 루트 INFO, `org.springframework`, `io.netty` WARN
- 개발: 루트 DEBUG, 기타 INFO

### 2.4 무엇을 로깅할 것인가
| Yes | No |
|-----|-----|
| domain event 발생 (entity ID 동봉) | 메서드 진입/종료 (DEBUG 이하) |
| 외부 API 호출 시작/완료/실패 | 사용자 답변 본문 (민감) |
| 비동기 작업 발행/소비 | API 키, 토큰 (보안) |
| 인증 실패 (rate-limit·alert 대상) | request body 전체 (volume) |

### 2.5 구조화 필드 컨벤션
- 모든 ID는 별도 필드 (`userId`, `sessionId`, `messageId`)로
- 외부 API 호출은 `external.service`, `external.endpoint`, `external.latencyMs`, `external.status`
- 에러는 `error.code`, `error.message`, `error.stack` (stack은 짧게)

---

## 3. AI 요청 로깅 (US-30)

별도 테이블 `ai_request_logs`에 다음을 기록:

```
request_type    예: 'session.followup', 'resume.analyze'
model_name      예: 'gemini-3.1-pro', 'gemini-3.1-flash', 'whisper-1'
input_tokens    토큰 카운트
output_tokens
latency_ms
status          'SUCCESS' | 'FAILED' | 'TIMEOUT'
error_message
```

**활용**:
- 모델별 평균 지연·비용 대시보드
- 실패율 알림 (5분 윈도우 5% 초과 시)
- 사용자별 토큰 사용량 (남용 감지)

```sql
SELECT model_name, AVG(latency_ms), COUNT(*) AS reqs
FROM ai_request_logs
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY model_name;
```

---

## 4. 활동 로그 (US-31)

`activity_logs` 테이블에 사용자 행동 기록:

```
action          예: 'LOGIN', 'RESUME_UPLOADED', 'SESSION_STARTED', 'SESSION_COMPLETED'
resource_type   예: 'RESUME', 'SESSION'
resource_id
detail          JSONB (추가 컨텍스트)
ip_address
user_agent
```

**활용**:
- 사용자별 funnel 분석
- 이상 패턴 탐지 (1분에 100회 로그인 시도 등)

→ 자세한 액션 카탈로그는 `backend/src/main/java/com/stackup/stackup/log/CLAUDE.md` 참조

---

## 5. 헬스체크

```
GET /api/system/health
```
응답:
```json
{
  "status": "UP",
  "components": {
    "database":  { "status": "UP" },
    "rabbitmq":  { "status": "UP", "details": { "queues": 6 } },
    "s3":        { "status": "UP" },
    "aiServer":  { "status": "UP", "details": { "endpoint": "..." } }
  }
}
```

- Spring Boot Actuator + 커스텀 indicator
- K8s liveness: 단순 200 응답 (`/api/system/live`)
- K8s readiness: 의존성 포함 (`/api/system/ready`)

---

## 6. 메트릭 (Phase 2 도입)

Prometheus + Grafana 권장.

핵심 메트릭:
- `http_server_requests_seconds` (per endpoint, status)
- `rabbitmq_queue_depth`
- `ai_request_latency_ms` (per model)
- `interview_session_active` (current count)
- `sse_connections_active`

---

## 7. 알림 (Alerting)

| 트리거 | 채널 | 우선순위 |
|--------|------|----------|
| AI 평균 latency > 10s (5분 sliding) | Slack | P2 |
| `ai_request_logs.status = FAILED` 비율 > 5% | Slack | P1 |
| RabbitMQ DLQ depth > 0 | Slack + 이메일 | P1 |
| Core Server 5xx 비율 > 1% | Slack | P0 |
| DB connection pool exhausted | Slack + 이메일 | P0 |

---

## 8. 로컬 디버깅

### 트레이스 따라가기
```bash
# Core 로그에서 traceId 잡기
docker logs stackup-core | grep '9f4e5b'

# AI 로그
docker logs stackup-ai | grep '9f4e5b'

# RabbitMQ 메시지 추적
# 관리 콘솔 → Queues → 메시지 헤더 'x-trace-id' 확인
```

### 슬로우 쿼리
- PostgreSQL `log_min_duration_statement = 500ms`
- 운영은 1000ms, 개발은 100ms

---

## 9. PII (Personal Identifiable Information) 마스킹

운영 로그에 자동 마스킹할 패턴:
- 이메일: `***@***`
- 전화번호: `010-****-****`
- GitHub access token: `ghp_***`
- JWT: `eyJ***`

`backend/src/main/java/com/stackup/stackup/common/log/PiiMasker.java` 단일 책임 클래스.
