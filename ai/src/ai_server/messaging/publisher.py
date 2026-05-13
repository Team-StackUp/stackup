from __future__ import annotations

from datetime import datetime, timezone
from uuid import uuid4

import structlog
from aio_pika import DeliveryMode, ExchangeType, Message
from pydantic import BaseModel

from ai_server.messaging.connection import RabbitConnection
from ai_server.model.envelope import Envelope, MessageContext

log = structlog.get_logger(__name__)


class CallbackPublisher:
    def __init__(
        self,
        connection: RabbitConnection,
        exchange_name: str,
        publisher_name: str,
    ) -> None:
        self._connection = connection
        self._exchange_name = exchange_name
        self._publisher_name = publisher_name
        self._exchange = None  # set in open()

    async def open(self) -> None:
        self._exchange = await self._connection.channel.declare_exchange(
            self._exchange_name,
            ExchangeType.TOPIC,
            durable=True,
            passive=True,  # 정의 파일이 이미 만들었음. 우리는 잡기만.
        )

    async def publish(
        self,
        *,
        routing_key: str,
        message_type: str,
        payload: BaseModel,
        trace_id: str,
        correlation_id: str,
        context: MessageContext,
        version: str = "v1",
    ) -> None:
        if self._exchange is None:
            raise RuntimeError("CallbackPublisher not opened")

        envelope = Envelope[type(payload)].model_validate(  # type: ignore[misc]
            {
                "messageId": str(uuid4()),
                "messageType": message_type,
                "version": version,
                "traceId": trace_id,
                "publishedAt": datetime.now(timezone.utc),
                "publisher": self._publisher_name,
                "payload": payload.model_dump(by_alias=True),
                "context": context.model_dump(by_alias=True),
            }
        )

        body = envelope.model_dump_json(by_alias=True).encode("utf-8")
        msg = Message(
            body=body,
            content_type="application/json",
            content_encoding="utf-8",
            delivery_mode=DeliveryMode.PERSISTENT,
            message_id=envelope.message_id,
            correlation_id=correlation_id,
            headers={"x-trace-id": trace_id, "x-attempt": 0},
        )
        await self._exchange.publish(msg, routing_key=routing_key)
        log.info(
            "rabbit.publish",
            exchange=self._exchange_name,
            routing_key=routing_key,
            message_id=envelope.message_id,
            correlation_id=correlation_id,
            trace_id=trace_id,
        )
