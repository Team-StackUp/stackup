import json
from datetime import datetime, timezone
from uuid import uuid4

import pytest
from pydantic import BaseModel, ValidationError

from ai_server.model.envelope import Envelope, MessageContext


class _Payload(BaseModel):
    foo: int


def _sample(payload: dict, **overrides: object) -> dict:
    base = {
        "messageId": str(uuid4()),
        "messageType": "analyze.resume",
        "version": "v1",
        "traceId": "trace-123",
        "publishedAt": "2026-05-08T00:00:00Z",
        "publisher": "core-server",
        "payload": payload,
        "context": {"userId": 1},
    }
    base.update(overrides)
    return base


def test_envelope_parses_minimum_fields() -> None:
    raw = _sample({"foo": 7})
    env = Envelope[_Payload].model_validate(raw)
    assert env.message_type == "analyze.resume"
    assert env.payload.foo == 7
    assert env.context.user_id == 1
    assert env.context.session_id is None


def test_envelope_ignores_unknown_fields() -> None:
    raw = _sample({"foo": 1, "newField": "ok"}, extraField="ignored")
    raw["context"]["futureKey"] = "ignored"
    env = Envelope[_Payload].model_validate(raw)
    assert env.payload.foo == 1


def test_envelope_rejects_missing_required_field() -> None:
    raw = _sample({"foo": 1})
    del raw["traceId"]
    with pytest.raises(ValidationError):
        Envelope[_Payload].model_validate(raw)


def test_envelope_serializes_camel_case() -> None:
    raw = _sample({"foo": 1})
    env = Envelope[_Payload].model_validate(raw)
    dumped = json.loads(env.model_dump_json(by_alias=True))
    assert "messageId" in dumped
    assert "messageType" in dumped
    assert "traceId" in dumped
    assert "publishedAt" in dumped
    assert dumped["context"]["userId"] == 1


def test_message_context_session_id_optional() -> None:
    ctx = MessageContext.model_validate({"userId": 5, "sessionId": 42})
    assert ctx.user_id == 5
    assert ctx.session_id == 42
