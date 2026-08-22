# `ai_server/` — 패키지 가이드

> AI 서버의 모든 코드는 이 패키지 안에 있다. 모듈 간 의존성과 신규 기능 추가 절차를 정의.

상위: [`/ai/CLAUDE.md`](../../CLAUDE.md)

---

## 1. 모듈 의존성 그래프

```
api ──┐
      ├──→ analyzer ──→ chain ──→ (LLM)
messaging                  │
      └──→ voice    ──→    │
              │            │
              └──→ rag ────┴──→ storage (S3)
                            └──→ core (httpx, Core internal API)

config: 모두가 의존
model: 모두가 의존 (Pydantic 스키마)
observability: chain 에 붙는 LangChain 콜백 — core 경유로 호출 로그 POST
```

원칙:
- `model`, `config`는 어디서든 import 가능 (가장 아래)
- `api`, `messaging`은 진입점 — 비즈니스 로직 import만, 비즈니스 로직이 이들을 import 금지
- 비즈니스 모듈(`analyzer`, `chain`, `rag`, `voice`)은 서로 호출 가능, 단 `chain`은 가장 아래

---

## 2. 모듈별 가이드

### `config/`
환경 설정만. 다른 모듈을 import하지 않는다.

```python
# config/settings.py
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # 모든 환경변수 정의
    ...

settings = Settings()  # singleton
```

### `model/`
- RabbitMQ envelope 모델 (`envelope.py`)
- 메시지 페이로드 — `model/messages/` 하위에 도메인별 파일
  (`analyze.py`, `questions.py`, `followup.py`, `feedback.py`, `voice.py`, `tts.py`, `realtime.py`)
- LLM 응답 schema (Pydantic) — 체인의 구조화 출력 검증에 사용
- 모든 페이로드 모델은 `_config.camel_config()` 로 wire 필드명을 camelCase 로 직렬화

```python
# model/messages/questions.py (발췌)
class QuestionPoolCallbackPayload(BaseModel):
    model_config = camel_config()

    session_id: int
    kind: CallbackKind = "POOL"
    questions: list[GeneratedQuestion] = []
    status: GenerationStatus = "OK"
```

### `api/`
- FastAPI router 정의
- 헬스체크 + 내부 디버그 endpoint만
- 외부 인증 X (Core가 처리), 내부 망에서만 호출됨

### `messaging/`
- aio-pika consumer / publisher
- 큐별 consumer 는 `messaging/consumers/{name}_consumer.py` 로 분리
  (resume/repository/web/cover_letter/questions/followup/feedback/voice/tts)
- 조립·기동은 `runner.py` 의 `MessagingRuntime` (§3), 연결은 `connection.py`,
  콜백 발행은 `publisher.py`, 멱등은 `idempotency.py`(`LruIdempotencyStore`),
  RealTime 직접 발행은 `progress.py`(분석 진행, user 채널)·`session_notify.py`(델타/오디오/질문 풀·피드백 생성 진행, 세션 채널)
- 모든 consumer는 envelope parsing → trace_context → 비즈니스 핸들러 호출 패턴

```python
# messaging/consumers/resume_consumer.py (패턴)
async def consume(message: AbstractIncomingMessage) -> None:
    async with message.process(requeue=False):
        envelope = parse_envelope(message)
        if await is_processed(envelope.message_id):
            return  # 멱등
        with trace_context(envelope.trace_id):
            await resume_analyzer.handle(envelope.payload)
            await mark_processed(envelope.message_id)
```

### `analyzer/`
- 분석 use case 단위 (`resume_analyzer.py`, `repository_analyzer.py`, `web_resume_analyzer.py`)
- 소스 추출 추상화는 `analyzer/sources/` (PDF/GitHub/웹/텍스트), 임베딩 인제스트는 `_embedding_step.py`
- 외부 입력 → 내부 모듈 조합 → 결과 publish. 피드백 생성은 analyzer 가 아니라
  `messaging/consumers/feedback_consumer.py` + `chain/feedback_generation_chain.py` 에 있다
- LLM 호출 자체는 `chain/`으로 위임

### `chain/`
- LangChain 체인 정의 (`document_analysis_chain.py`, `question_generation_chain.py`,
  `followup_generation_chain.py`, `feedback_generation_chain.py`, `pdf_vision.py`, `sentence_split.py`)
- `chain/prompts/` 하위에 prompt 템플릿 (모든 프롬프트가 한 곳에)
- 출력 파싱은 별도 모듈 없이 각 체인 안에서 Pydantic 구조화 출력으로 검증

### `rag/`
- 청킹 (`chunker.py` — `MarkdownChunker`)
- 임베딩 생성 (`embedder.py` — provider 추상화 + Gemini/Mock 구현)
- 검색은 rag 모듈이 아니라 `core/client.py: search_embeddings` (Core `POST /api/internal/embeddings/search`)

### `core/`
- Core 내부 API httpx 클라이언트 (`client.py`) — `X-Internal-API-Key` 인증
- GitHub token 위임 · 임베딩 upsert/검색 · AI 호출 로그 기록 (엔드포인트 목록:
  [`/docs/messaging.md §10`](../../../docs/messaging.md))

### `voice/`
- `voice/stt/` — interface + provider impls (배치 Whisper/Deepgram + 라이브 Deepgram Live)
- `voice/tts/` — provider 추상화 (Gateway/Gemini/OpenAI/Mock)
- `voice/analysis/` — WPM, filler, silence (`metrics.py`)

### `storage/`
- `ObjectStorage` 추상화 (`base.py`) + `s3.py` / `local_fs.py` 구현, `factory.py` 로 토글
- 객체 key 는 각 사용처에서 [`/docs/storage.md §2`](../../../docs/storage.md) 컨벤션대로 조립 (전용 헬퍼 모듈 없음)

### `observability/`
- `llm_logging_callback.py` — LangChain `AsyncCallbackHandler`. 토큰/latency 측정 후
  `core/client.py: record_ai_log` 로 Core `POST /api/internal/ai-logs` (fire-and-forget)

---

## 3. 진입점

### REST (FastAPI)
- `api/health.py` — 헬스체크
- `api/voice_stream.py` — `/internal/voice/stream` WS (RealTime 이 프록시한 실시간 음성 답변, RT3)

### MQ Consumer
- `messaging/runner.py` — `MessagingRuntime` 이 의존성(체인·스토리지·Core 클라이언트·notifier)을
  조립하고 모든 consumer 를 시작/종료하는 단일 entry
- `main.py` lifespan 에서 `runtime.start()` / `runtime.stop()` 호출

```python
# main.py 의 lifespan (실제 패턴)
@asynccontextmanager
async def lifespan(app: FastAPI):
    runtime = MessagingRuntime(settings)
    app.state.messaging = runtime
    try:
        await runtime.start()
        yield
    finally:
        await runtime.stop()
```

---

## 4. 비동기 (async) 가이드

- 모든 IO는 async (`httpx.AsyncClient`, `aio-pika`, `aiobotocore`)
- 동기 라이브러리가 필요하면 `run_in_executor`로 wrap
- LangChain은 `chain.ainvoke(...)` 사용 (async 메서드)
- 한 consumer 안에서 여러 LLM 호출이 필요하면 `asyncio.gather`

---

## 5. 에러 분류

| 분류 | 처리 |
|------|------|
| 일시 장애 (네트워크, 5xx, rate limit) | retry (envelope `x-attempt` 증가) → 재시도 한도 초과 시 DLQ |
| 영구 실패 (PDF 손상, 입력 검증 실패) | 즉시 `ai.callback.*.failed` 발행 + `retriable: false` |
| 코드 버그 (parsing 오류 등) | 예외 raise → consumer가 catch → DLQ + 알림 |

```python
class AnalysisError(Exception):
    def __init__(self, code: str, message: str, retriable: bool):
        ...
```

---

## 6. 신규 기능 패턴

이력서 분석 (US-09)을 예로 들면:

1. `model/messages/{name}.py`에 `ResumeAnalyzeRequest`, `ResumeAnalyzed`, `ResumeFailed` 정의
2. `messaging/consumers/resume_consumer.py` 구현 (envelope parse → handler 호출)
3. `analyzer/resume_analyzer.py` 구현
   ```python
   async def handle(req: ResumeAnalyzeRequest) -> None:
       pdf_bytes = await storage.get(req.s3_key)
       text = extract_text(pdf_bytes)
       result = await resume_chain.ainvoke({"text": text})
       md_key = f"analyzed/resume/{req.resume_id}/summary.md"
       await storage.put(md_key, result.markdown)
       chunks = chunker.split(result.markdown)
       embeddings = await embedder.embed(chunks)
       await core_client.upsert_embeddings(document_id=req.analyzed_document_id, ...)
       await publisher.publish_callback(ResumeAnalyzed(...))
   ```
4. `chain/{name}_chain.py` (prompt + LLM + Pydantic 구조화 출력)
5. 단위 테스트 (mock LLM)
6. 통합 테스트 (Testcontainer RabbitMQ + MinIO)
7. `messaging/runner.py` 의 `MessagingRuntime` 에 consumer 등록

---

## 7. 안티패턴

- ❌ `time.sleep()` (async 코드에서) → `await asyncio.sleep()`
- ❌ Settings를 함수 인자로 매번 전달 → singleton 사용 OR DI 패턴
- ❌ 프롬프트 인라인 작성 → `chain/prompts/`로
- ❌ LLM 응답 `.json.loads()` → Pydantic parser 사용
- ❌ Consumer에서 무한 retry → 한도 + DLQ
- ❌ 한 모듈에 수백 줄 함수 → 책임 분리
