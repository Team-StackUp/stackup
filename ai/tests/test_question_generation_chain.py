from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from ai_server.chain.question_generation_chain import (
    GeneratedQuestionPool,
    LlmQuestionGenerator,
    _format_recent_questions,
)


def test_format_recent_questions_empty():
    assert _format_recent_questions(None) == "(없음)"
    assert _format_recent_questions([]) == "(없음)"


def test_format_recent_questions_bullets():
    assert _format_recent_questions(["질문 A", "질문 B"]) == "- 질문 A\n- 질문 B"


@pytest.mark.asyncio
async def test_generate_forwards_formatted_recent_questions_to_chain():
    chain = AsyncMock()
    chain.ainvoke = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    generator = LlmQuestionGenerator(chain)

    await generator.generate(
        job_categories=["BACKEND", "FRONTEND"],
        mode="TECHNICAL",
        max_questions=3,
        context="ctx",
        recent_questions=["이전 질문"],
    )

    chain_input = chain.ainvoke.call_args.args[0]
    assert chain_input["recent_questions"] == "- 이전 질문"
    assert chain_input["job_categories"] == "BACKEND, FRONTEND"
