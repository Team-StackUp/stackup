from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_server.chain.question_generation_chain import (
    GeneratedQuestionPool,
    LlmQuestionGenerator,
    _format_focus_areas,
)
from ai_server.model.messages.questions import (
    GeneratedQuestion,
    GenerateQuestionsRequest,
)


class TestFormatFocusAreas:
    def test_no_focus_says_do_not_skew(self) -> None:
        text = _format_focus_areas(None)

        assert "지정 없음" in text
        # 지정이 없을 때 특정 영역으로 쏠리라는 지시가 새어 들어가면 안 된다.
        assert "낮았습니다" not in text

    def test_empty_list_is_same_as_none(self) -> None:
        assert _format_focus_areas([]) == _format_focus_areas(None)

    # 라벨만 넘기면 LLM 이 제각각 해석한다 — 축마다 질문 설계 관점을 붙여야 한다.
    @pytest.mark.parametrize(
        ("area", "expected"),
        [
            ("TECHNICAL", "동작 원리"),
            ("LOGIC", "근거"),
            ("COMMUNICATION", "전달력"),
        ],
    )
    def test_known_area_carries_guidance(self, area: str, expected: str) -> None:
        text = _format_focus_areas([area])

        assert area in text
        assert expected in text

    def test_multiple_areas_are_all_listed(self) -> None:
        text = _format_focus_areas(["LOGIC", "COMMUNICATION"])

        assert "LOGIC" in text
        assert "COMMUNICATION" in text

    # Core 가 새 축을 추가해도 프롬프트가 깨지지 않아야 한다.
    def test_unknown_area_degrades_gracefully(self) -> None:
        text = _format_focus_areas(["SOMETHING_NEW"])

        assert "SOMETHING_NEW" in text


class TestGeneratorPassesFocusAreas:
    @pytest.mark.asyncio
    async def test_focus_areas_reach_the_prompt(self) -> None:
        chain = MagicMock()
        chain.ainvoke = AsyncMock(
            return_value=GeneratedQuestionPool(
                questions=[
                    GeneratedQuestion(category="CS_FUNDAMENTAL", question="q?"),
                ]
            )
        )
        generator = LlmQuestionGenerator(chain)

        await generator.generate(
            job_categories=["BACKEND"],
            mode="TECHNICAL",
            max_questions=3,
            context="ctx",
            focus_areas=["LOGIC"],
        )

        variables = chain.ainvoke.await_args.args[0]
        assert "LOGIC" in variables["focus_areas"]

    @pytest.mark.asyncio
    async def test_omitting_focus_areas_still_fills_the_slot(self) -> None:
        chain = MagicMock()
        chain.ainvoke = AsyncMock(
            return_value=GeneratedQuestionPool(
                questions=[
                    GeneratedQuestion(category="CS_FUNDAMENTAL", question="q?"),
                ]
            )
        )
        generator = LlmQuestionGenerator(chain)

        await generator.generate(
            job_categories=["BACKEND"], mode="TECHNICAL", max_questions=3, context="ctx"
        )

        # 프롬프트 변수는 항상 채워져야 한다 — 비면 템플릿 렌더가 KeyError 로 깨진다.
        assert "지정 없음" in chain.ainvoke.await_args.args[0]["focus_areas"]


class TestRequestModel:
    def test_focus_areas_default_to_empty(self) -> None:
        req = GenerateQuestionsRequest(sessionId=1, mode="TECHNICAL")

        assert req.focus_areas == []

    def test_focus_areas_parse_from_camel_case(self) -> None:
        req = GenerateQuestionsRequest(
            sessionId=1, mode="TECHNICAL", focusAreas=["LOGIC", "COMMUNICATION"]
        )

        assert req.focus_areas == ["LOGIC", "COMMUNICATION"]
