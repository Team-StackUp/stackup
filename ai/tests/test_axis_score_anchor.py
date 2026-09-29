"""평가위원은 **자기 축 기준값만** 봐야 한다.

전에는 네 축(technical/logic/communication/overall)이 담긴 같은 블록을 모든 위원에게
똑같이 넘겼다. 프롬프트는 "해당 축 기준값이 있으면 ±15점 이내"라고 하지만, 기준값
라벨(`logic_score`)과 위원의 축 이름(`논리·인과관계 명확성`)이 달라 위원이 스스로 짝을
찾아야 했고, 무엇보다 `overall_score` 라는 **어느 위원도 써서는 안 될 앵커**가 보였다.
"""

from __future__ import annotations

import pytest

from ai_server.chain.feedback_generation_chain import (
    EvaluatorResult,
    PanelFeedbackGenerator,
    axis_of,
)
from ai_server.messaging.consumers.feedback_consumer import build_axis_score_basis
from ai_server.model.messages.feedback import FeedbackMessageItem, MessageEvaluation


def _answers(*evals: MessageEvaluation) -> list[FeedbackMessageItem]:
    return [
        FeedbackMessageItem(
            id=i, sequence_number=i, role="INTERVIEWEE", content="답변", evaluation=e
        )
        for i, e in enumerate(evals, start=1)
    ]


def test_axis_of_maps_every_domain_evaluator_to_technical():
    # 직군 위원은 여러 명이라 key 가 tech:{직군} 이다. 하나라도 빠지면 그 위원만
    # 전체 기준값으로 떨어져 조용히 옛 동작이 된다.
    assert axis_of("tech:BACKEND") == "technical"
    assert axis_of("tech:SALES") == "technical"
    assert axis_of("technical") == "technical"
    assert axis_of("logic") == "logic"
    assert axis_of("communication") == "communication"


def test_each_axis_sees_only_its_own_number():
    basis = build_axis_score_basis(
        _answers(
            MessageEvaluation(
                specificity=5, logic=1, structure="FULL_STAR", correctness=4
            )
        )
    )

    # 각 축의 기준값은 서로 다른 값이라 섞이면 바로 드러난다.
    assert "직무 역량 기준값 ≈ 80" in basis["technical"]
    assert "논리 기준값 ≈ 20" in basis["logic"]
    assert "전달력 기준값 ≈ 100" in basis["communication"]

    for axis, text in basis.items():
        # overall 은 어느 위원에게도 보이면 안 된다.
        assert "overall" not in text.lower(), f"{axis}: {text}"
        # 남의 축 라벨도 보이면 안 된다.
        others = {"직무 역량", "논리", "전달력"} - {
            {"technical": "직무 역량", "logic": "논리", "communication": "전달력"}[axis]
        }
        for other in others:
            assert f"{other} 기준값" not in text, f"{axis} 가 {other} 를 본다: {text}"


def test_axis_basis_is_empty_without_per_answer_evaluations():
    # 평가가 없으면 축별 기준값도 없다 — 패널은 전체 문자열로 폴백한다.
    assert build_axis_score_basis([]) == {}


class _CapturingChain:
    def __init__(self) -> None:
        self.calls: list[dict] = []

    async def ainvoke(self, payload):
        self.calls.append(payload)
        return EvaluatorResult(score=70)


@pytest.mark.asyncio
async def test_panel_gives_each_evaluator_its_own_basis():
    chain = _CapturingChain()
    gen = PanelFeedbackGenerator(chain)

    await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=3,
        end_reason="x",
        transcript="t",
        rag_context="(none)",
        voice_analysis_summary="",
        score_basis="(전체 기준값 — 쓰이면 안 된다)",
        axis_score_basis={
            "technical": "TECH-ONLY",
            "logic": "LOGIC-ONLY",
            "communication": "COMM-ONLY",
        },
    )

    seen = {c["dimension_name"]: c["score_basis"] for c in chain.calls}
    assert seen["직무 역량·깊이"] == "TECH-ONLY"
    assert seen["논리·인과관계 명확성"] == "LOGIC-ONLY"
    assert seen["명료성·구조화·전달력"] == "COMM-ONLY"


@pytest.mark.asyncio
async def test_panel_falls_back_to_shared_basis_when_axis_basis_absent():
    """축별 기준값이 없으면(평가 0건) 기존 동작을 유지해야 한다 — 앵커가 통째로
    사라지면 점수가 캘리브레이션 없이 흔들린다."""
    chain = _CapturingChain()
    gen = PanelFeedbackGenerator(chain)

    await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=3,
        end_reason="x",
        transcript="t",
        rag_context="(none)",
        voice_analysis_summary="",
        score_basis="SHARED",
    )

    assert {c["score_basis"] for c in chain.calls} == {"SHARED"}
