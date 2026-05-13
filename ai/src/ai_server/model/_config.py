from pydantic import ConfigDict
from pydantic.alias_generators import to_camel


def camel_config() -> ConfigDict:
    """Shared Pydantic ConfigDict for camelCase JSON ↔ snake_case Python.

    Used by all message envelope and payload models in `ai_server.model`.
    `populate_by_name=True` allows constructing instances with snake_case kwargs.
    `extra="ignore"` provides forward compatibility with new wire fields.
    """
    return ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )
