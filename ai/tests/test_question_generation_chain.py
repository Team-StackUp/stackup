from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from ai_server.chain.question_generation_chain import (
    GeneratedQuestionPool,
    LlmQuestionGenerator,
    _format_recent_questions,
    _format_self_introduction,
)


def test_format_recent_questions_empty():
    assert _format_recent_questions(None) == "(없음)"
    assert _format_recent_questions([]) == "(없음)"


def test_format_recent_questions_bullets():
    assert _format_recent_questions(["질문 A", "질문 B"]) == "- 질문 A\n- 질문 B"


def test_format_self_introduction_empty():
    assert _format_self_introduction(None) == "(자기소개 없음)"
    assert _format_self_introduction("   ") == "(자기소개 없음)"


def test_format_self_introduction_trims():
    assert _format_self_introduction("  안녕하세요 백엔드 3년차입니다.  ") == (
        "안녕하세요 백엔드 3년차입니다."
    )


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
        self_introduction="결제 시스템을 만든 백엔드입니다.",
    )

    chain_input = chain.ainvoke.call_args.args[0]
    assert chain_input["recent_questions"] == "- 이전 질문"
    assert chain_input["job_categories"] == "BACKEND, FRONTEND"
    assert chain_input["self_introduction"] == "결제 시스템을 만든 백엔드입니다."


@pytest.mark.asyncio
async def test_generate_defaults_self_introduction_when_missing():
    chain = AsyncMock()
    chain.ainvoke = AsyncMock(return_value=GeneratedQuestionPool(questions=[]))
    generator = LlmQuestionGenerator(chain)

    await generator.generate(
        job_categories=["BACKEND"],
        mode="TECHNICAL",
        max_questions=3,
        context="ctx",
    )

    assert chain.ainvoke.call_args.args[0]["self_introduction"] == "(자기소개 없음)"
