from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any, TypeVar

import structlog
from aio_pika.abc import AbstractIncomingMessage
from pydantic import BaseModel

from ai_server.messaging.idempotency import LruIdempotencyStore
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import Envelope

log = structlog.get_logger(__name__)

ReqT = TypeVar("ReqT", bound=BaseModel)


def format_error_message(exc: Exception) -> str:
    """str(exc) 는 LLM 응답 본문·입력 repr 까지 담길 수 있다 — 로그·와이어 크기 상한."""
    return f"{type(exc).__name__}: {exc}"[:500]


def classify_failure(exc: Exception, *, unexpected_code: str) -> tuple[str, bool]:
    """(error_code, retriable) 분류의 단일 구현. TypeError(LLM 출력 스키마 불일치)는
    같은 입력으로 재시도해도 똑같이 죽는다 → retriable=false. 그 외는 컨슈머별 계약 코드
    (questions/followup: GENERATION_FAILED, feedback: UNEXPECTED)."""
    if isinstance(exc, TypeError):
        return "GENERATION_SCHEMA_INVALID", False
    return unexpected_code, True


async def consume_with_failure_signal(
    message: AbstractIncomingMessage,
    *,
    domain: str,
    envelope_type: type[Envelope[ReqT]],
    idempotency: LruIdempotencyStore,
    publisher: CallbackPublisher,
    routing_key: str,
    message_type: str,
    process: Callable[[Envelope[ReqT]], Awaitable[BaseModel]],
    failed_payload: Callable[[ReqT, Exception], BaseModel],
    done_fields: Callable[[Any], dict[str, Any]] | None = None,
) -> None:
    """생성 컨슈머(질문 풀·꼬리질문·피드백) 공용 실패 신호 가드.

    같은 무기한 대기 버그를 컨슈머마다 다른 깊이로 세 번 고쳐 온 메커니즘의 단일 구현:
    - envelope 파싱 실패는 재시도 무의미 → raise → DLQ (콜백 대상 식별 불가).
    - `process` 의 전 구간(컨텍스트 빌드·진행 이벤트·생성·payload 조립) 예외는 항상
      FAILED 콜백으로 신호하고 ack — 콜백 없이 DLQ 로만 격리되면 Core 가 실패를 모른 채
      세션이 "생성 중"에 무기한 멈춘다.
    - 성공 payload 의 발행 실패는 생성 실패가 아니다 — FAILED 로 오인 발행하지 않고
      원 예외로 DLQ 에 보내 재처리 가능하게 남긴다.
    - 콜백을 하나도 못 낸 채 DLQ 로 가는 경로는 멱등 마킹을 되돌린다(unmark) —
      재주입 시 duplicate skip 으로 삼켜지지 않게.
    """
    async with message.process(requeue=False):
        try:
            envelope = envelope_type.model_validate_json(message.body)
        except Exception as exc:
            log.error(
                f"{domain}.parse.failed",
                error=str(exc),
                delivery_tag=message.delivery_tag,
            )
            raise

        if idempotency.is_seen_then_mark(envelope.message_id):
            log.info(
                f"{domain}.idempotent.skip",
                message_id=envelope.message_id,
                trace_id=envelope.trace_id,
            )
            return

        async def _publish(payload: BaseModel) -> None:
            await publisher.publish(
                routing_key=routing_key,
                message_type=message_type,
                payload=payload,
                trace_id=envelope.trace_id,
                correlation_id=envelope.message_id,
                context=envelope.context,
            )

        session_id = getattr(envelope.payload, "session_id", None)

        try:
            payload = await process(envelope)
        except Exception as exc:  # noqa: BLE001
            log.exception(
                f"{domain}.generate.failed",
                message_id=envelope.message_id,
                session_id=session_id,
                trace_id=envelope.trace_id,
            )
            try:
                # 팩토리 자체가 죽어도(검증 오류 등) 같은 안전망을 태운다 —
                # try 밖이면 unmark 없이 DLQ 로 가서 재주입이 duplicate skip 으로 삼켜진다.
                fallback = failed_payload(envelope.payload, exc)
                await _publish(fallback)
            except Exception:  # noqa: BLE001
                log.exception(
                    f"{domain}.failed_callback.publish_failed",
                    message_id=envelope.message_id,
                    session_id=session_id,
                    trace_id=envelope.trace_id,
                )
                idempotency.unmark(envelope.message_id)
                raise exc
            return

        try:
            await _publish(payload)
        except Exception:
            idempotency.unmark(envelope.message_id)
            raise
        log.info(
            f"{domain}.generate.done",
            message_id=envelope.message_id,
            session_id=session_id,
            trace_id=envelope.trace_id,
            **(done_fields(payload) if done_fields is not None else {}),
        )
