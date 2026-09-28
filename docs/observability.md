# 옵저버빌리티

> 분산 추적 + 구조화 로깅 + AI 비용·지연 모니터링.

---

## 0. AI 호출 비용 귀속 (`ai_request_logs.session_id`)

**2026-09-28 이전에는 `stt.transcribe` 말고 전부 NULL 이었다.** 2026-08-06 이래 쌓인
`generate.*`·`feedback.*`·`tts.synthesize`·`embedding.embed` 로그 전부가 세션을 모른 채
기록됐다. `record_ai_call` 과 `CoreAiLogCallback` 은 둘 다 `session_id` 를 **받을 수 있게**
만들어져 있었는데 실제로 넘기는 호출부가 voice consumer 하나뿐이었다.

그래서 "이 면접이 얼마나 들었나", "어느 세션이 게이트웨이 크레딧을 태웠나" 를 답할 수 없었다 —
2026-09-17 에 실제로 크레딧이 소진돼 운영 AI 가 402 로 죽었을 때 정확히 이 질문을 못 했다.

체인 빌더마다 인자로 넘기면 LangChain 콜백 생성 지점 6곳과 체인 팩토리 시그니처가 전부
오염된다. 관측값은 횡단 관심사이므로 **traceId 와 같은 방식으로 컨텍스트에 싣는다** —
`trace_context(trace_id, session_id=…, user_id=…)` 가 컨슈머 진입점에서 묶고,
`record_ai_call` 과 `CoreAiLogCallback` 이 명시 인자가 없을 때 거기서 읽는다.
명시 인자가 있으면 그쪽이 이긴다.

세션이 없는 흐름(문서 분석)은 그대로 NULL 이다 — 억지로 채우지 않는다.

---

## 1. 분산 추적 (X-Trace-Id)

### 1.1 ID 생성
- 클라이언트 또는 Nginx Gateway가 부여 (UUID v4)
- 한 요청의 모든 후속 처리(REST/Queue/SSE)에 동일 traceId 전파
  - **2026-09-28 이전에는 큐를 건너면 끊겼다.** 컨슈머가 엔벨로프의 traceId 를 MDC 로
    되돌리지 않아, 이어서 발행하는 메시지에 `RabbitMessagePublisher` 가 **새 traceId** 를
    붙였다. 지금은 5개 `@RabbitListener` 가 `TraceContext.runWithTraceId` 로 감싸 원 요청의
    id 아래에서 실행된다.
  - AI 서버도 2026-09-28 부터 싣는다 — `observability/trace.py` 의 `trace_context` 가
    엔벨로프 traceId 를 structlog contextvars 에 묶어 그 구간 모든 로그에 자동 부착한다.
    (그전까지는 `ai/CLAUDE.md` 가 `trace_context` 를 패턴으로 제시했는데 **구현이 없었다**.)
    uvicorn 의 액세스 로그(`GET /health`)에는 여전히 없다 — 요청 단위 미들웨어는 미도입.

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

### 2.1 출력 포맷

**평문이다. JSON 이 아니다.** (2026-09-28 정정 — 이 절은 원래 JSON 예시를 싣고 있었지만
그런 설정이 존재한 적이 없다. `logback-spring.xml` 도 없고 인코더 의존성도 없다.)

JSON 을 도입하지 않은 이유: 로그를 모아 가는 수집기가 없고 실제로 읽는 경로는 `docker logs`
다 — 거기서는 평문이 더 낫다. 수집기를 도입할 때 encoder 를 바꾸면 된다.

패턴은 `application.yml` 의 `logging.pattern.console` 이고 **traceId 를 포함한다**:

```
2026-09-28T13:50:01.123Z  INFO [3f2a…-9c1b] [io-38010-exec-5] c.s.s.session.application.SessionService : ...
2026-09-28T13:50:02.004Z  WARN [no-trace]   [scheduling-2]    c.s.s.s.a.StaleFollowupSweeper           : ...
```

`%X{traceId:-no-trace}` 라서 traceId 가 없는 흐름(스케줄러 스위퍼 등)은 `no-trace` 로 뜬다 —
빈칸으로 두면 "추적이 끊긴 것"과 "원래 요청이 없는 것"이 구분되지 않는다.

> **2026-09-28 이전에는 traceId 가 로그에 아예 없었다.** `TraceIdFilter` 가 MDC 를 채우고
> 엔벨로프가 큐 너머로 실어 날랐는데 **로그 패턴이 MDC 를 참조하지 않았다.** 메시지 문자열에
> 직접 박아 넣은 몇 줄(`SecurityConfig`, `GlobalExceptionHandler`)만 보였다. MDC 를 채우는
> 코드가 사실상 아무 일도 하지 않고 있었던 셈이다.

## 3. AI 요청 로깅 (US-30)

별도 테이블 `ai_request_logs`에 다음을 기록:

```
request_type    예: 'session.followup', 'resume.analyze'
model_name      예: 'gemini-3.1-pro-preview', 'gemini-3.5-flash-lite', 'whisper-1'
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

### 3.1 기록 경로 — 두 가지

| 경로 | 구현 | 대상 |
|------|------|------|
| LangChain 콜백 | `observability/llm_logging_callback.py` | 체인을 타는 LLM 호출(질문·꼬리질문·피드백·문서 분석) |
| 직접 기록 | `observability/ai_call_log.py` | 체인 밖 외부 호출 — STT(배치·라이브)·TTS·임베딩 |

콜백은 체인 경로만 잡는다. 그래서 한동안 TTS·임베딩·라이브 STT 는 `ai_request_logs` 에
**한 줄도 남지 않았고**, 운영 TTS 요청 245건 중 43건이 실패했는데 원인을 사후에 알 수 없었다.
외부 API 를 새로 붙일 때는 이 표에 어느 경로로 기록되는지 함께 적는다.

**현재 `request_type` 목록**: `generate.questions` · `generate.followup.stream` ·
`generate.feedback.{panel,synthesis,coaching,self_intro}` · `analyze.document` ·
`stt.transcribe` · `stt.live` · `tts.synthesize` · `embedding.embed`

재시도가 있는 경로(STT·임베딩)는 **시도마다 한 행**을 남기고 `error_message` 앞에 `[n/N]` 을
붙인다. 실패율을 셀 때 분모가 '요청'이 아니라 '시도'가 되므로, 사용자에게 보인 실패만 세려면
`[N/N]` 으로 끝난 행만 센다. 이 규약 덕분에 재시도율 자체를 관측할 수 있다.

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
    "database": { "name": "database", "status": "UP" },
    "rabbitmq": { "name": "rabbitmq", "status": "UP" },
    "s3":       { "name": "s3",       "status": "UP" },
    "aiServer": { "name": "aiServer", "status": "UP" }
  }
}
```

> **상태만 담는다.** 이 엔드포인트는 permitAll 이라 인증 없이 열린다. Actuator 는 기본값이
> `show-details: never` 인데 예전엔 이 서비스가 descriptor 에서 상세를 직접 꺼내 그 보호를
> 우회했다 — RabbitMQ 버전·S3 버킷명·큐 이름과 적체량이 그대로 나갔다.
> 상세가 필요하면 **호스트에서** Spring 자체 `/actuator/health` 를 본다(nginx 가 외부로
> 라우팅하지 않는다).

- Spring Boot Actuator 의 컴포넌트를 이름으로 조회해 재구성한다(`SystemHealthService`).
- **응답 키와 Actuator 컴포넌트 키는 다르다.** Actuator 키는 Spring 이 등록하는 빈 이름에서
  접미사를 뗀 값이라 `database`→`db`, `rabbitmq`→**`rabbit`** 이다. 여기를 틀리면 조회가
  null 을 돌려줘 그 컴포넌트가 **영구 UNKNOWN** 이 된다(에러가 아니라 조용한 무응답).
- `s3` 는 `headBucket`(엔드포인트·자격증명·버킷을 한 번에 확인), `aiServer` 는 **작업 큐의
  컨슈머 수**로 판단한다. AI 를 HTTP 로 찌르지 않는 이유는 아키텍처 §4.1 — Core→AI 는
  RabbitMQ 전용이고, 컨슈머 수가 더 정확한 신호이기도 하다(프로세스 생존보다 "큐를 실제로
  구독 중인가"가 중요). 컨슈머 0 이면 DOWN, 브로커 자체가 죽었으면 UNKNOWN(그건 rabbitmq
  컴포넌트가 알려준다).
- K8s liveness: 단순 200 응답 (`/api/system/live`)
- K8s readiness: 의존성 포함 (`/api/system/ready`)
- **컨테이너 healthcheck 는 `/actuator/health/readiness`** 를 쓴다(docker-compose).
  readiness 그룹은 `readinessState + db + rabbit` 로 명시돼 있다 — 백엔드가 자기 일을 하려면
  반드시 필요한 것만. s3·aiServer 는 종합(`/actuator/health`)에만 들어간다: **AI 가 죽었다고
  백엔드를 rotation 에서 빼면 정작 멀쩡한 로그인·히스토리까지 끊긴다.**
- 그룹 멤버십 검증(`validate-group-membership`)은 기본값 그대로 **켜 둔다**. 이름을 틀리면
  부팅이 실패해 배포 게이트에서 잡힌다 — 조용히 UNKNOWN 이 되는 것보다 낫다(§실제 사례).

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

## 8.1 로그 테이블 보존

DB 에 쌓이는 로그·휘발성 테이블은 정리 주기가 있다. 없으면 "short-lived 레코드"라는 전제가
코드로는 지켜지지 않는다(루트 CLAUDE.md).

| 테이블 | 보존 | 정리 |
|---|---|---|
| `ai_request_logs` | 90일 (`OBSERVABILITY_AI_LOG_RETENTION_DAYS`) | `AiRequestLogSweeper` |
| `processed_messages` | 30일 (`MESSAGING_PROCESSED_MESSAGE_RETENTION_DAYS`) | `ProcessedMessageSweeper` |
| `refresh_tokens` | 만료 즉시 | `ExpiredRefreshTokenSweeper` |
| `oauth_states` | 만료 즉시 | 발급 시 self-cleaning |

보존 기간의 성격이 테이블마다 다르다.

- `ai_request_logs` 는 **비용 추이**가 가치다 — 짧게 잡으면 "지난 학기 대비 토큰이 얼마나
  늘었나" 에 답할 수 없다. 지운다고 동작이 깨지지는 않는다.
- `processed_messages` 는 **멱등성 보장 기간**이다 — 너무 짧으면 DLQ 에서 늦게 재주입된
  메시지가 중복 처리된다(질문 중복·피드백 재생성). 공간 문제가 아니다.

> **미구현**: `activity_logs`(US-31, 사용자 행동 로그)는 테이블·엔티티·리포지토리만 있고
> 읽기도 쓰기도 없다. 구현 시 여기에 보존 정책을 함께 정한다.

---

## 9. PII (Personal Identifiable Information) 마스킹

`backend/src/main/java/com/stackup/stackup/common/log/PiiMasker.java` 가 마스킹 함수를 제공한다.
지원 패턴: 이메일 / 전화번호 / GitHub access token / JWT.

> **자동 마스킹은 걸려 있지 않다.** `PiiMasker` 는 **호출하는 곳에서만** 동작하는 유틸이고,
> 현재 호출부가 없다. 로그 파이프라인에 필터로 꽂혀 있지 않으므로 "로그에 남겨도 알아서
> 가려지겠지"라고 가정하면 안 된다 — 애초에 남기지 않는 것이 규약이다(`docs/security.md §7`).

### 왜 전역 필터로 꽂지 않았나

`PiiMasker.mask()` 를 Logback 컨버터로 전 로그에 적용하면 **분산 추적이 깨진다.**
전화번호 패턴이 구분자를 포함한 9자리 이상 숫자열을 모두 잡기 때문이다:

```
traceId=01234567-89ab-cdef-0123-456789abcdef
  → traceId=***-***-6789ab-cdef-***-***-6789abcdef
session ended at 1755993600000
  → session ended at ***-***-0000
```

`X-Trace-Id` 상관관계는 Core·AI·RealTime 을 잇는 유일한 수단이라(§1) 이걸 잃는 대가가
"혹시 모를 PII"보다 크다. 지금 백엔드·AI 로그는 모두 **식별자만** 남기고 본문을 남기지
않으므로(감사 확인) 전역 필터의 실익도 없다.

**쓰는 방법**: 값이 PII 임을 아는 지점에서 `maskEmail`·`maskPhoneNumber` 같은 개별 함수를
직접 부른다. 임의의 로그 문자열에 `mask()` 를 통째로 거는 용도가 아니다.
