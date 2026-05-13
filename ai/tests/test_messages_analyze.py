import pytest
from pydantic import ValidationError

from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    ResumeAnalyzeRequest,
)


def test_resume_request_parses() -> None:
    req = ResumeAnalyzeRequest.model_validate(
        {"resumeId": 42, "s3Key": "resumes/raw/123/abc.pdf"}
    )
    assert req.resume_id == 42
    assert req.s3_key == "resumes/raw/123/abc.pdf"


def test_resume_request_requires_fields() -> None:
    with pytest.raises(ValidationError):
        ResumeAnalyzeRequest.model_validate({"resumeId": 42})


def test_callback_success_serializes_camel_case() -> None:
    cb = AnalysisCallbackPayload(
        target_type="RESUME",
        target_id=42,
        status="ANALYZED",
        summary="[ECHO] not yet analyzed",
        tech_stack=[],
        document_s3_key="echo/resume/42/echo.md",
        embedding_chunk_count=0,
    )
    dumped = cb.model_dump(by_alias=True)
    assert dumped["targetType"] == "RESUME"
    assert dumped["targetId"] == 42
    assert dumped["status"] == "ANALYZED"
    assert dumped["techStack"] == []
    assert dumped["documentS3Key"] == "echo/resume/42/echo.md"
    assert dumped["embeddingChunkCount"] == 0


def test_callback_target_type_must_be_known() -> None:
    with pytest.raises(ValidationError):
        AnalysisCallbackPayload(
            target_type="UNKNOWN",  # type: ignore[arg-type]
            target_id=1,
            status="ANALYZED",
        )
