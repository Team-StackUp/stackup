# AI Server — Claude 컨텍스트

> StackUp AI 서버. **Python 3.13 + FastAPI + LangChain**. RabbitMQ consumer로 동작하며 LLM 호출, RAG, 음성 분석을 담당.

상위 컨텍스트: [`/CLAUDE.md`](../CLAUDE.md) · 횡단 관심사: [`/docs/`](../docs/README.md)

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Python 3.13 (`.python-version` 고정) |
| 패키지 매니저 | **uv** (`uv.lock` 락파일) |
| Framework | FastAPI 0.135+ |
| ASGI | uvicorn (standard) |
| 스키마 | Pydantic 2.x + pydantic-settings |
| HTTP | httpx |
| MQ | aio-pika (async AMQP) |
| 객체 스토리지 | boto3 (S3 호환) |
| LLM | LangChain 1.x (core + community) |
| 로깅 | structlog |
| 빌드 | hatchling |
| 테스트 | pytest + pytest-asyncio |
| 포맷/린트 | black, flake8, pylint |

---

## 2. 디렉토리 구조

```
ai/
├── pyproject.toml
├── uv.lock
├── .python-version
├── Dockerfile
├── .env.example
├── src/
│   └── ai_server/
│       ├── __init__.py
│       ├── main.py              # FastAPI 앱 팩토리
│       ├── api/                 # FastAPI 라우터 (health 등)
│       ├── config/
│       │   └── settings.py      # pydantic-settings (env)
│       ├── analyzer/            # 이력서·레포·웹 분석 오케스트레이션
│       │   ├── resume_analyzer.py
│       │   └── sources/         # SourceExtractor 추상화 + 구현체 (PDF; repo/web 후속)
│       ├── chain/               # LangChain 체인 + 프롬프트
│       │   ├── document_analysis_chain.py
│       │   └── prompts/
│       ├── storage/             # ObjectStorage 추상화 (local fs / s3)
│       ├── messaging/           # RabbitMQ consumer/publisher
│       ├── model/               # Pydantic 모델 (envelope, 메시지 페이로드)
│       ├── rag/                 # (계획) 청킹·임베딩·pgvector
│       └── voice/               # (Phase 2) STT/TTS, 음성 분석
└── tests/
```

---

## 3. 책임 매트릭스

| 모듈 | 책임 |
|------|------|
| `main.py` | FastAPI 부트스트랩, lifespan에서 RabbitMQ consumer 시작 |
| `config/settings.py` | 환경변수 → 타입 안전 설정 객체 |
| `api/` | 헬스체크 + (필요 시) 내부 디버그 API |
| `messaging/` | aio-pika consumer (큐별), publisher |
| `analyzer/` | 이력서/레포 분석 use case (PDF 추출, GitHub fetch, 마크다운 생성) |
| `chain/` | LangChain prompt template + chain composition |
| `rag/` | 청킹, 임베딩 생성, pgvector 검색 호출 (Core API 경유) |
| `voice/` | STT/TTS 어댑터, WPM/filler/silence 분석 |
| `storage/` | S3 GET/PUT 래퍼 |
| `model/` | RabbitMQ envelope, request/response Pydantic 모델 |

---

## 4. 비책임 (명시적)

- ❌ PostgreSQL 직접 접근 — Core 서버 API 경유 또는 RabbitMQ 메시지에 데이터 동봉
- ❌ JWT 발급·검증 — 인증은 Core
- ❌ REST CRUD API 노출 — 외부 트리거는 RabbitMQ만
- ❌ 사용자 인증 (내부 통신만) — `api/`는 헬스체크 / 내부 도구

---

## 5. 메시징 (RabbitMQ)

본 서버는 RabbitMQ **consumer**로 작동.

| Queue | Routing key | 상태 |
|-------|-------------|------|
| `ai.analyze.resume` | `analyze.resume` | 본 구현 (PDF → MD) |
| `ai.analyze.repository` | `analyze.repository` | 본 구현 (GitHub README + tree + 소스 sampling) |
| `ai.analyze.web` | `analyze.web` | 본 구현 (URL → trafilatura) |
| `ai.generate.questions` | `generate.questions` | 본 구현 (Pro 모델, 질문 풀 생성, US-18) |
| `ai.generate.followup` | `generate.followup` | 본 구현 (Flash 모델, 답변 평가+꼬리질문, US-19) |
| `ai.generate.tts` | `generate.tts` | 본 구현 (질문 음성화, OpenAI TTS → S3 → `callback.tts`) |

콜백 발행: `ai.callback.{type}` 익스체인지.
상세 envelope/스키마/재시도: [`/docs/messaging.md`](../docs/messaging.md).

### consumer 패턴 (aio-pika)
```python
async def consume_resume_analyze(message: AbstractIncomingMessage) -> None:
    async with message.process(requeue=False):  # auto ack on exit
        envelope = parse_envelope(message)
        with trace_context(envelope.trace_id):
            await resume_analyzer.handle(envelope.payload)
```

### 멱등 처리
- envelope의 `messageId`를 PostgreSQL `processed_messages` 테이블 (Core API 경유) 또는 AI 프로세스 인메모리 LRU 캐시에 기록
- 이미 존재하면 skip + ACK
- (Redis 미사용 — DB 1쿼리 부담을 감수하거나, 인메모리만 쓰고 재시작 시 RabbitMQ delivery_tag로 보조)

---

## 6. LLM 사용 패턴

### 6.1 게이트웨이
모든 LLM 호출은 **Mindlogic CNU AC Gateway** (OpenAI 호환) 단일 엔드포인트로 통과한다.
- `LLM_BASE_URL` (default `https://factchat-cloud.mindlogic.ai/v1/gateway`)
- `LLM_API_KEY` (학교 발급, secret/`.env`로만 주입)
- 모델 카탈로그: Claude / GPT / Gemini / xAI / Perplexity / Solar / Gemma 등을 단일 키로 사용.
- 게이트웨이 교체(자체 OpenAI 키, vLLM 등) 시 `LLM_BASE_URL` 한 줄만 변경.

### 6.2 모델 선택
| 시점 | 모델 (env override 가능) | 용도 |
|------|--------------------------|------|
| 세션 시작 | Pro (`gemini-3.1-pro-preview` 기본) | 질문 풀 (품질) |
| 세션 중 | Flash (`gemini-3.1-flash-lite-preview` 후보) | 꼬리질문 (저지연 < 3s) |
| 분석 (이력서/레포) | Pro | 마크다운 구조화 |

설정은 `settings.py` + 환경변수로 모델명 주입 (코드에 하드코딩 금지).

### 6.3 LangChain 사용 (OpenAI 호환 클라이언트)
```python
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI

prompt = ChatPromptTemplate.from_messages([
    ("system", "당신은 면접관입니다..."),
    ("human", "이력서: {resume}\n질문 후보: ..."),
])
llm = ChatOpenAI(
    model=settings.llm_pro_model,
    base_url=settings.llm_base_url,
    api_key=settings.llm_api_key,
)
chain = prompt | llm | PydanticOutputParser(pydantic_object=...)
```

- 모든 프롬프트는 `chain/prompts/{name}.py` 단일 위치
- 프롬프트 변경은 PR로 (커밋 메시지에 의도 명시)
- 응답은 **반드시 schema validation** (Pydantic) — LLM 결과를 그대로 신뢰 X

### 6.4 호출 로깅
호출 시작/완료/실패를 Core 서버에 RabbitMQ로 보내 `ai_request_logs`에 기록 — 또는 자체 endpoint POST.
필드: `request_type`, `model_name`, `input_tokens`, `output_tokens`, `latency_ms`, `status`.

상세: [`/docs/observability.md §3`](../docs/observability.md).

---

## 7. RAG 파이프라인

### 7.1 인제스트 (본 구현)
1. 마크다운 입력 (`analyzer/_embedding_step.chunk_embed_and_upsert`)
2. 청킹 — `rag/chunker.MarkdownChunker` (`RecursiveCharacterTextSplitter`, 기본 1000/200)
3. 임베딩 생성 — `rag/embedder.EmbeddingProvider`. 현재 구현체:
   - `MockEmbeddingProvider` (default) — 차원 결정 보류, e2e 흐름 검증용
   - `openai` / `ollama` 구현체는 후속 PR
4. Core API 호출 — `CoreClient.upsert_embeddings(document_id, model, dim, chunks)` →
   `PUT /api/internal/documents/{id}/embeddings` (idempotent upsert)

### 7.2 검색
1. 쿼리 텍스트 → 임베딩
2. Core API: `POST /api/internal/embeddings/search` (top_k 검색)
3. 검색 결과 + 추가 메타데이터 → 프롬프트에 주입

> Core가 pgvector 단일 진입점을 제공하므로 AI는 직접 pg 호출 X.

---

## 8. 음성 처리 (Phase 2)

- **STT: OpenAI Whisper API 채택** (한국어 + 개발 영어 혼용 환경 정확도 우수)
  - 비용: $0.006 / 분 (1시간 면접 ≈ ₩500 / USD ≈ $0.36)
  - 셀프호스팅 옵션: `whisper.cpp` 또는 `faster-whisper` (GPU 권장, 비용 ↓ but 운영 부담 ↑)
  - 브라우저 내장 SpeechRecognition API는 정확도 부족으로 채택 안 함
- **TTS: OpenAI TTS 채택** (`voice/tts/`) — 질문(INTERVIEWER) 메시지 음성화. `TtsProvider` 추상화 + `OpenAiTtsProvider`(`gpt-4o-mini-tts`, mp3)/`MockTtsProvider`, `build_tts_provider` factory(`TTS_PROVIDER=auto`면 OPENAI_API_KEY 보유 시 openai). `generate.tts` consumer 가 합성 → S3 PUT → `callback.tts` 발행.
- 추상화 계층 두기: `voice/stt/base.py` (interface), `voice/stt/whisper_api.py`, `voice/tts/base.py` + `voice/tts/{provider}.py`
- 분석:
  - WPM = words / minutes
  - 간투어: 한국어 정규식 `r"\b(음+|어+|그+)\b"` 카운트
  - 침묵: VAD (Voice Activity Detection) 라이브러리 결과 합산

---

## 9. 환경 변수

`config/settings.py`의 `Settings` 클래스가 진실 공급원 (single source of truth).
필수 변수는 default 없음 → 부팅 실패로 누락 감지.

```python
class Settings(BaseSettings):
    rabbitmq_url: str
    s3_endpoint_url: str
    s3_access_key: str
    s3_secret_key: str
    s3_bucket_name: str
    openai_api_key: str = ""
    google_api_key: str = ""
    llm_pro_model: str = "gemini-3.1-pro"
    llm_flash_model: str = "gemini-3.1-flash"
    embedding_model: str = "text-embedding-004"
    embedding_dim: int = 768
    core_server_base_url: str = "http://core:8080"
```

전체 환경변수 카탈로그: [`/docs/environment.md §4`](../docs/environment.md).

---

## 10. 로깅 (structlog)

```python
import structlog
log = structlog.get_logger()

log.info("resume.analyze.start", resume_id=42, trace_id=trace_id)
log.error("resume.analyze.failed", resume_id=42, error_code="PDF_PARSE_FAILED", exc_info=True)
```

- JSON 출력 (운영) / human pretty (로컬)
- 컨텍스트 변수로 `trace_id`, `user_id` 자동 주입
- 민감정보 (이력서 본문, 답변 본문) 절대 X
- 자세한 정책: [`/docs/observability.md`](../docs/observability.md)

---

## 11. 테스트

```bash
uv run pytest                       # 전체
uv run pytest tests/test_rag.py     # 특정 파일
uv run pytest -k "embedding"        # 키워드
```

- 비동기 테스트는 `pytest-asyncio` (`@pytest.mark.asyncio`)
- LLM 호출은 mock (LangChain `FakeListLLM`)
- RabbitMQ는 Testcontainer 또는 `aio-pika` mock
- 자세한 전략: [`/docs/testing-strategy.md`](../docs/testing-strategy.md)

---

## 12. 코드 스타일

- black (line-length 88)
- pyproject.toml 기준
- 함수/변수: `snake_case`
- 상수: `UPPER_SNAKE_CASE`
- 클래스: `PascalCase`
- 타입 힌트 필수 (`from __future__ import annotations` 없이 PEP 604 union `int | None`)
- async first — sync IO 사용 시 명시적 이유

상세 공통 규약: [`/docs/coding-conventions.md`](../docs/coding-conventions.md).

---

## 13. 빌드·실행

```bash
uv sync                                              # 의존성 설치
uv run uvicorn ai_server.main:app --reload          # 개발 실행
uv run python -m ai_server.messaging.runner         # consumer 단독 실행 (도입 후)

# Docker
docker build -t stackup-ai .
docker run --env-file .env -p 8000:8000 stackup-ai
```

`docker-compose.yml`에 ai 서비스 추가는 추후 (현재는 Core/PG/MQ/MinIO만 있음).

---

## 14. 새 기능 추가 절차

1. 메시지 스키마 정의 → `model/messages.py`
2. RabbitMQ routing key 결정 → [`/docs/messaging.md`](../docs/messaging.md) 갱신
3. consumer 작성 → `messaging/{name}_consumer.py`
4. 비즈니스 로직 → `analyzer/` / `chain/` / `voice/` 적절한 모듈
5. 프롬프트 (LLM 호출 시) → `chain/prompts/{name}.py`
6. 단위 테스트 (LLM mock) + 통합 테스트 (Testcontainer)
7. 본 문서 §3, §5 갱신

---

## 15. 안티패턴

- ❌ 프롬프트를 코드 안 곳곳에 산재시키기 → `chain/prompts/`로 모은다
- ❌ LLM 응답을 파싱 없이 그대로 사용 → 항상 Pydantic schema validation
- ❌ 동기 라이브러리(`requests`, `pika`) 사용 → async 라이브러리(`httpx`, `aio-pika`)
- ❌ 트랜잭션이 필요한 작업 (PG 직접 접근) → Core API 호출
- ❌ messageId 멱등 체크 누락 → 중복 처리 위험
- ❌ 프롬프트에 사용자 답변을 그대로 system message로 → injection 가능. 반드시 user message로.

---

## 16. 현재 상태 (2026-05 기준)

- FastAPI 부트스트랩 + 헬스체크
- 분석 consumer 본 구현 — `analyze.resume` / `analyze.repository` / `analyze.web`:
  - PDF·GitHub Repo·웹 URL 소스 추출 추상화 (`analyzer/sources/`)
  - LLM 분석 (`chain/document_analysis_chain.py`, Gemini Pro + Pydantic 출력 파서)
  - 분석 MD를 스토리지에 저장
  - `callback.analysis` 발행 (status `ANALYZED` / `FAILED`, retriable 플래그 포함)
- 면접 consumer 본 구현 — `generate.questions` (US-18) / `generate.followup` (US-19):
  - 질문 풀 생성 (Pro 모델, `chain/question_generation_chain.py`)
  - 꼬리질문 + 답변 평가 (Flash 모델, `chain/followup_generation_chain.py`)
  - 콜백: `callback.questions` (`kind=POOL|FOLLOWUP`)
- **임베딩 본 구현** (`rag/`): `MarkdownChunker` + `GeminiEmbeddingProvider` (1536d, `gemini-embedding-001`).
  운영/개발 default 는 gemini, 테스트는 `MockEmbeddingProvider`.
- **스토리지 추상화** (`storage/`): `S3Storage`(기본) / `LocalFilesystemStorage`. `STORAGE_BACKEND` 토글.
- **LLM 호출 로깅 본 구현** (`observability/llm_logging_callback.py`, US-30):
  LangChain `AsyncCallbackHandler` 가 토큰/latency 측정 → Core `/api/internal/ai-logs` POST.
- **질문 TTS consumer 본 구현** (`messaging/consumers/tts_consumer.py`, `voice/tts/`):
  `generate.tts` 수신 → OpenAI TTS 합성(`OpenAiTtsProvider`, mock fallback) → S3 PUT(`interview/tts/{sessionId}/{messageId}.mp3`) → `callback.tts` 발행.
- 음성 분석(STT/WPM/filler) 모듈은 Phase 2

각 도입 시 본 문서 갱신.
