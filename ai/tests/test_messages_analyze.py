import pytest
from pydantic import ValidationError

from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    ResumeAnalyzeRequest,
)


def test_resume_request_parses() -> None:
    req = ResumeAnalyzeRequest.model_validate(
        {"resumeId": 42, "filePath": "resumes/raw/123/abc.pdf"}
    )
    assert req.resume_id == 42
    assert req.file_path == "resumes/raw/123/abc.pdf"


def test_resume_request_requires_fields() -> None:
    with pytest.raises(ValidationError):
        ResumeAnalyzeRequest.model_validate({"resumeId": 42})


def test_resume_request_serializes_camel_case() -> None:
    req = ResumeAnalyzeRequest(resume_id=7, file_path="r/7.pdf")
    dumped = req.model_dump(by_alias=True)
    assert dumped == {"resumeId": 7, "filePath": "r/7.pdf"}


def test_callback_success_serializes_camel_case() -> None:
    cb = AnalysisCallbackPayload(
        target_type="RESUME",
        target_id=42,
        status="ANALYZED",
        summary="요약",
        tech_stack=["Python", "FastAPI"],
        document_path="analyzed/resume/42/summary.md",
        embedding_chunk_count=0,
    )
    dumped = cb.model_dump(by_alias=True)
    assert dumped["targetType"] == "RESUME"
    assert dumped["targetId"] == 42
    assert dumped["status"] == "ANALYZED"
    assert dumped["techStack"] == ["Python", "FastAPI"]
    assert dumped["documentPath"] == "analyzed/resume/42/summary.md"
    assert dumped["embeddingChunkCount"] == 0


def test_callback_failure_carries_error_fields() -> None:
    cb = AnalysisCallbackPayload(
        target_type="RESUME",
        target_id=42,
        status="FAILED",
        error_code="EMPTY_PDF_TEXT",
        error_message="no text",
        retriable=False,
    )
    dumped = cb.model_dump(by_alias=True)
    assert dumped["status"] == "FAILED"
    assert dumped["errorCode"] == "EMPTY_PDF_TEXT"
    assert dumped["errorMessage"] == "no text"
    assert dumped["retriable"] is False


def test_callback_target_type_must_be_known() -> None:
    with pytest.raises(ValidationError):
        AnalysisCallbackPayload(
            target_type="UNKNOWN",  # type: ignore[arg-type]
            target_id=1,
            status="ANALYZED",
        )
