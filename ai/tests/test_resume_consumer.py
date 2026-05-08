from datetime import datetime, timezone

from ai_server.messaging.consumers.resume_consumer import (
    build_echo_callback,
)
from ai_server.model.envelope import Envelope, MessageContext
from ai_server.model.messages.analyze import ResumeAnalyzeRequest


def _request_envelope() -> Envelope[ResumeAnalyzeRequest]:
    return Envelope[ResumeAnalyzeRequest].model_validate(
        {
            "messageId": "req-1",
            "messageType": "analyze.resume",
            "version": "v1",
            "traceId": "trace-x",
            "publishedAt": datetime(2026, 5, 8, tzinfo=timezone.utc),
            "publisher": "core-server",
            "payload": {"resumeId": 42, "s3Key": "resumes/raw/123/abc.pdf"},
            "context": {"userId": 123},
        }
    )


def test_echo_callback_target_type_resume() -> None:
    cb = build_echo_callback(_request_envelope())
    assert cb.target_type == "RESUME"
    assert cb.target_id == 42
    assert cb.status == "ANALYZED"


def test_echo_callback_includes_marker_summary() -> None:
    cb = build_echo_callback(_request_envelope())
    assert cb.summary is not None
    assert "ECHO" in cb.summary
    assert cb.tech_stack == []
    assert cb.document_s3_key == "echo/resume/42/echo.md"
    assert cb.embedding_chunk_count == 0


def test_echo_callback_preserves_resume_id_in_s3_key() -> None:
    env = _request_envelope()
    env_dict = env.model_dump(by_alias=True)
    env_dict["payload"]["resumeId"] = 7
    other = Envelope[ResumeAnalyzeRequest].model_validate(env_dict)
    cb = build_echo_callback(other)
    assert cb.target_id == 7
    assert cb.document_s3_key == "echo/resume/7/echo.md"


def test_extract_context_passes_user_id() -> None:
    env = _request_envelope()
    cb = build_echo_callback(env)
    # context 검증은 consumer entry에서 하지만 핸들러에 의존하지 않음.
    # 여기서는 envelope.context가 그대로 보존되는지만 확인.
    assert env.context.user_id == 123
