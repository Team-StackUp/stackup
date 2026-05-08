from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

TargetType = Literal["RESUME", "REPOSITORY"]
AnalysisStatus = Literal["ANALYZED", "FAILED"]


def _camel_config() -> ConfigDict:
    return ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class ResumeAnalyzeRequest(BaseModel):
    model_config = _camel_config()

    resume_id: int
    s3_key: str


class AnalysisCallbackPayload(BaseModel):
    model_config = _camel_config()

    target_type: TargetType
    target_id: int
    status: AnalysisStatus
    summary: str | None = None
    tech_stack: list[str] = []
    document_s3_key: str | None = None
    embedding_chunk_count: int = 0
    error_code: str | None = None
    error_message: str | None = None
    retriable: bool | None = None
