from datetime import datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

PayloadT = TypeVar("PayloadT", bound=BaseModel)


def _camel_config() -> ConfigDict:
    return ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class MessageContext(BaseModel):
    model_config = _camel_config()

    user_id: int | None = None
    session_id: int | None = None


class Envelope(BaseModel, Generic[PayloadT]):
    model_config = _camel_config()

    message_id: str
    message_type: str
    version: str = "v1"
    trace_id: str
    published_at: datetime
    publisher: str
    payload: PayloadT
    context: MessageContext = Field(default_factory=MessageContext)
