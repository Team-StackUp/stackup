import pytest
from pydantic import ValidationError

from ai_server.model.messages.analyze import (
    AnalysisCallbackPayload,
    RepositoryAnalyzeRequest,
    ResumeAnalyzeRequest,
    WebResumeAnalyzeRequest,
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


def test_repository_request_parses_with_default_branch() -> None:
    req = RepositoryAnalyzeRequest.model_validate(
        {"repositoryId": 5, "repoFullName": "user/repo"}
    )
    assert req.repository_id == 5
    assert req.repo_full_name == "user/repo"
    assert req.default_branch == "main"


def test_repository_request_serializes_camel_case() -> None:
    req = RepositoryAnalyzeRequest(
        repository_id=5, repo_full_name="user/repo", default_branch="dev"
    )
    dumped = req.model_dump(by_alias=True)
    assert dumped == {
        "repositoryId": 5,
        "repoFullName": "user/repo",
        "defaultBranch": "dev",
    }


def test_web_resume_request_parses() -> None:
    req = WebResumeAnalyzeRequest.model_validate(
        {"resumeId": 9, "url": "https://example.com/me"}
    )
    assert req.resume_id == 9
    assert req.url == "https://example.com/me"


def test_callback_accepts_web_target_type() -> None:
    cb = AnalysisCallbackPayload(
        target_type="WEB",
        target_id=9,
        status="ANALYZED",
        summary="ok",
        document_path="analyzed/web-resume/9/summary.md",
    )
    dumped = cb.model_dump(by_alias=True)
    assert dumped["targetType"] == "WEB"
    assert dumped["documentPath"] == "analyzed/web-resume/9/summary.md"
