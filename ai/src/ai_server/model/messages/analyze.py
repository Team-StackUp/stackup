from typing import Literal

from pydantic import BaseModel

from ai_server.model._config import camel_config

TargetType = Literal["RESUME", "REPOSITORY", "WEB"]
AnalysisStatus = Literal["ANALYZED", "FAILED"]


class ResumeAnalyzeRequest(BaseModel):
    model_config = camel_config()

    resume_id: int
    file_path: str


class RepositoryAnalyzeRequest(BaseModel):
    model_config = camel_config()

    repository_id: int
    repo_full_name: str
    default_branch: str = "main"


class WebResumeAnalyzeRequest(BaseModel):
    model_config = camel_config()

    resume_id: int
    url: str


class AnalysisCallbackPayload(BaseModel):
    model_config = camel_config()

    target_type: TargetType
    target_id: int
    status: AnalysisStatus
    summary: str | None = None
    tech_stack: list[str] = []
    document_path: str | None = None
    embedding_chunk_count: int = 0
    error_code: str | None = None
    error_message: str | None = None
    retriable: bool | None = None
