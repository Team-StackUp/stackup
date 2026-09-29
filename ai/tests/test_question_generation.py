from __future__ import annotations

import pytest

from ai_server.chain.question_generation_chain import GeneratedQuestionPool

# ── 산업 맥락 ────────────────────────────────────────────────────────────────
#
# 직군만으로는 질문 맥락이 얇다. 같은 "생산·품질" 이라도 반도체 공정과 건설 현장은 묻는
# 것이 전혀 다르다. 산업은 자유 입력이라 CHECK·Literal 이 없고, 그래서 **프롬프트까지
# 실제로 도달하는지**를 테스트가 봐야 한다.


def _capture_chain():
    """ainvoke 에 들어온 입력을 그대로 잡아두는 가짜 체인."""
    seen: dict = {}

    class _Chain:
        async def ainvoke(self, payload):
            seen.update(payload)
            return GeneratedQuestionPool(questions=[])

    return _Chain(), seen


@pytest.mark.asyncio
async def test_industry_reaches_the_prompt():
    from ai_server.chain.question_generation_chain import LlmQuestionGenerator

    chain, seen = _capture_chain()
    await LlmQuestionGenerator(chain).generate(
        job_categories=["MANUFACTURING"],
        mode="TECHNICAL",
        max_questions=3,
        context="",
        industry="반도체",
    )

    assert seen["industry"] == "반도체"


@pytest.mark.asyncio
async def test_missing_industry_becomes_explicit_none():
    """빈 값을 그대로 흘리면 프롬프트에 'None' 이나 빈 줄이 박힌다 —
    LLM 이 그걸 산업 이름으로 읽을 수 있다. 명시적으로 '(지정 없음)' 을 넣는다."""
    from ai_server.chain.question_generation_chain import LlmQuestionGenerator

    for value in (None, "", "   "):
        chain, seen = _capture_chain()
        await LlmQuestionGenerator(chain).generate(
            job_categories=["SALES"],
            mode="PERSONALITY",
            max_questions=3,
            context="",
            industry=value,
        )
        assert seen["industry"] == "(지정 없음)", f"industry={value!r}"


def test_prompt_has_industry_slot_and_guidance():
    """슬롯만 있고 지침이 없으면 LLM 이 산업을 장식으로 흘려보낸다."""
    from ai_server.chain.prompts.question_generation import HUMAN_PROMPT, SYSTEM_PROMPT

    assert "{industry}" in HUMAN_PROMPT
    # 산업을 근거로 착각해 없는 지식을 지어내면 안 된다 — 그 경계가 지침에 있어야 한다.
    assert "지어내" in SYSTEM_PROMPT
