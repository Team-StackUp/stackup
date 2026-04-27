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
                            └──→ httpx (Core API)

config: 모두가 의존
model: 모두가 의존 (Pydantic 스키마)
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
- RabbitMQ envelope 모델
- 도메인 객체 (`AnalyzedResume`, `QuestionPool`, `FollowUpResult`)
- LLM 응답 schema (Pydantic) — `OutputParser`에 사용

```python
# model/messages.py
class ResumeAnalyzeRequest(BaseModel):
    resume_id: int
    s3_key: str

class ResumeAnalyzed(BaseModel):
    resume_id: int
    summary: str
    tech_stack: list[str]
    document_s3_key: str
    embedding_chunk_count: int
```

### `api/`
- FastAPI router 정의
- 헬스체크 + 내부 디버그 endpoint만
- 외부 인증 X (Core가 처리), 내부 망에서만 호출됨

### `messaging/`
- aio-pika consumer / publisher
- 큐별 consumer 함수 분리
- 모든 consumer는 envelope parsing → trace_context → 비즈니스 핸들러 호출 패턴

```python
# messaging/resume_consumer.py
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
- use case 단위 (`resume_analyzer.py`, `repo_analyzer.py`, `feedback_generator.py`)
- 외부 입력 → 내부 모듈 조합 → 결과 publish
- LLM 호출 자체는 `chain/`으로 위임

### `chain/`
- LangChain 체인 정의
- `chain/prompts/` 하위에 prompt 템플릿 (모든 프롬프트가 한 곳에)
- `chain/parsers/` 출력 파서

### `rag/`
- 청킹 (`splitter.py`)
- 임베딩 생성 (`embedder.py`)
- 검색 어댑터 (Core API client `pgvector_client.py`)

### `voice/` (Phase 2)
- `voice/stt/` — interface + provider impls
- `voice/tts/`
- `voice/analysis/` — WPM, filler, silence

### `storage/`
- S3 client wrapper (`s3.py`)
- key 생성 헬퍼 (`keys.py`) — [`/docs/storage.md §2`](../../../docs/storage.md) 컨벤션 준수

---

## 3. 진입점

### REST (FastAPI)
- `api/health.py` — 헬스체크
- `api/internal/*` — Core가 호출할 수 있는 동기 endpoint (필요 시)

### MQ Consumer
- `messaging/runner.py` (도입 예정) — 모든 consumer를 시작하는 entry
- `main.py` lifespan에서 자동 시작 (또는 별도 프로세스로 분리 검토)

```python
# main.py 의 lifespan
@asynccontextmanager
async def lifespan(app: FastAPI):
    connection = await connect_robust(settings.rabbitmq_url)
    channel = await connection.channel()
    await start_resume_consumer(channel)
    await start_repo_consumer(channel)
    await start_session_consumer(channel)
    yield
    await connection.close()
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

1. `model/messages.py`에 `ResumeAnalyzeRequest`, `ResumeAnalyzed`, `ResumeFailed` 정의
2. `messaging/resume_consumer.py` 구현 (envelope parse → handler 호출)
3. `analyzer/resume_analyzer.py` 구현
   ```python
   async def handle(req: ResumeAnalyzeRequest) -> None:
       pdf_bytes = await s3.get(req.s3_key)
       text = extract_text(pdf_bytes)
       result = await resume_chain.ainvoke({"text": text})
       md_key = f"analyzed/resume/{req.resume_id}/summary.md"
       await s3.put(md_key, result.markdown)
       chunks = split(result.markdown)
       embeddings = await embedder.embed(chunks)
       await pgvector_client.upsert(req.resume_id, chunks, embeddings)
       await publisher.publish_callback(ResumeAnalyzed(...))
   ```
4. `chain/resume_analyzer_chain.py` (prompt + LLM + parser)
5. 단위 테스트 (mock LLM)
6. 통합 테스트 (Testcontainer RabbitMQ + MinIO)
7. main.py lifespan에 consumer 등록

---

## 7. 안티패턴

- ❌ `time.sleep()` (async 코드에서) → `await asyncio.sleep()`
- ❌ Settings를 함수 인자로 매번 전달 → singleton 사용 OR DI 패턴
- ❌ 프롬프트 인라인 작성 → `chain/prompts/`로
- ❌ LLM 응답 `.json.loads()` → Pydantic parser 사용
- ❌ Consumer에서 무한 retry → 한도 + DLQ
- ❌ 한 모듈에 수백 줄 함수 → 책임 분리
