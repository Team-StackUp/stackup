from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, Field

from ai_server.model._config import camel_config

PayloadT = TypeVar("PayloadT", bound=BaseModel)


class MessageContext(BaseModel):
    model_config = camel_config()

    user_id: int | None = None
    session_id: int | None = None
    document_id: int | None = None
    repository_id: int | None = None


class Envelope(BaseModel, Generic[PayloadT]):
    model_config = camel_config()

    message_id: str
    message_type: str
    version: str = "v1"
    trace_id: str
    published_at: datetime
    publisher: str
    payload: PayloadT
    context: MessageContext = Field(default_factory=MessageContext)
