"""정직한 "모르겠습니다" 가 오답처럼 감점되지 않아야 한다.

`_build_score_basis` 의 `_mean` 은 None 을 제외한다. 그래서 **0 과 null 의 차이가 곧
점수 차이**다. 그런데 스키마가 specificity/logic 을 required 로 두고 있어서:

- LLM 이 스키마대로 0 을 쓰면 → 모른다고 말한 것이 틀린 답과 같은 취급
- 프롬프트 지시대로 null 을 쓰면 → 검증 실패를 파서가 삼켜 **평가 전체가 사라짐**
  (structure 까지, 그리고 진짜 파싱 실패와 구분 불가)

어느 쪽도 맞지 않았다.
"""

from __future__ import annotations

from ai_server.chain.followup_generation_chain import parse_followup_result
from ai_server.messaging.consumers.feedback_consumer import _build_score_basis
from ai_server.model.messages.feedback import FeedbackMessageItem, MessageEvaluation

# AnswerEvaluation 은 AI→Core(꼬리질문 채점), MessageEvaluation 은 Core→AI(피드백 롤업).
# 받는 쪽은 처음부터 전부 nullable 이었고 보내는 쪽만 막고 있었다.


def _meta(spec, logic, structure="NONE", correctness=None) -> str:
    def j(v):
        return "null" if v is None else str(v)

    return (
        f"<intent>DONT_KNOW</intent><question>q</question>"
        f'<meta>{{"specificity": {j(spec)}, "logic": {j(logic)}, '
        f'"structure": "{structure}", "correctness": {j(correctness)}}}</meta>'
    )


def _answer(evaluation: MessageEvaluation | None) -> FeedbackMessageItem:
    return FeedbackMessageItem(
        id=1,
        sequence_number=2,
        role="INTERVIEWEE",
        content="답변",
        evaluation=evaluation,
    )


def test_null_scores_survive_parsing():
    """예전에는 여기서 ValidationError 가 나고 파서가 삼켜 평가가 통째로 사라졌다."""
    result = parse_followup_result(_meta(None, None))

    assert result.answer_evaluation is not None, "null 평가가 통째로 버려졌다"
    assert result.answer_evaluation.specificity is None
    assert result.answer_evaluation.logic is None
    # structure 는 살아남아야 한다 — 예전엔 이것까지 함께 사라졌다.
    assert result.answer_evaluation.structure == "NONE"


def test_null_is_excluded_from_aggregate_but_zero_is_not():
    """이 차이가 이 수정의 전부다."""
    good = MessageEvaluation(
        specificity=4, logic=4, structure="FULL_STAR", correctness=4
    )

    with_null = _build_score_basis(
        [
            _answer(good),
            _answer(MessageEvaluation(specificity=None, logic=None, structure="NONE")),
        ]
    )
    with_zero = _build_score_basis(
        [
            _answer(good),
            _answer(MessageEvaluation(specificity=0, logic=0, structure="NONE")),
        ]
    )

    # null 이면 좋은 답변 하나만 반영 → logic 4.0/5 → 80
    assert "logic_score ≈ 80" in with_null, with_null
    # 0 이면 평균이 반토막 난다 — 모른다고 말한 대가로 20점을 잃는다
    assert "logic_score ≈ 40" in with_zero, with_zero


def test_all_null_evaluations_report_no_basis_not_zero():
    """전부 모름이어도 0점이 아니라 '근거 없음' 이어야 한다."""
    basis = _build_score_basis(
        [
            _answer(MessageEvaluation(specificity=None, logic=None, structure="NONE")),
        ]
    )

    assert "logic_score ≈ 근거 없음" in basis, basis
    assert "≈ 0" not in basis, basis


def test_prompt_demands_null_not_low_score():
    """'낮게/null' 처럼 모호하면 LLM 이 매번 다르게 고른다."""
    from ai_server.chain.prompts.followup_generation import SYSTEM_PROMPT

    assert "반드시 null" in SYSTEM_PROMPT
    assert "낮게/null" not in SYSTEM_PROMPT
    # meta 스키마도 null 을 허용한다고 말해야 한다 — 안 그러면 LLM 이 숫자를 낸다.
    assert '"specificity": <0~5 또는 null>' in SYSTEM_PROMPT


def test_unscoreable_answer_does_not_drag_communication_via_structure():
    """`_STRUCTURE_SCORE["NONE"] = 0.0` 이라 채점 불가 답변의 구조를 빼지 않으면
    "평가할 내용 없음"이 "구조가 형편없음(0점)"으로 둔갑한다."""
    good = MessageEvaluation(
        specificity=4, logic=4, structure="FULL_STAR", correctness=4
    )
    unscoreable = MessageEvaluation(
        specificity=None, logic=None, structure="NONE", correctness=None
    )

    basis = _build_score_basis([_answer(good), _answer(unscoreable)])

    # 좋은 답변 하나만 반영: specificity 4/5, structure 5/5 → 평균 4.5/5 → 90
    assert "communication_score ≈ 90" in basis, basis


def test_structure_none_still_penalises_a_real_but_unstructured_answer():
    """구분 기준은 structure 값이 아니라 채점 가능 여부다.

    길게 답했는데 구조가 없는 것은 **정상 감점 사유**다 — 이것까지 빼면 안 된다.
    """
    rambling = MessageEvaluation(
        specificity=3, logic=3, structure="NONE", correctness=None
    )

    basis = _build_score_basis([_answer(rambling)])

    # specificity 3/5 + structure 0/5 → 평균 1.5/5 → 30
    assert "communication_score ≈ 30" in basis, basis


def test_basis_tells_the_llm_that_unscoreable_is_not_a_penalty():
    """기준값만 주고 이유를 안 주면 LLM 이 '답변 수가 적네' 로 읽고 깎을 수 있다."""
    basis = _build_score_basis(
        [
            _answer(MessageEvaluation(specificity=4, logic=4, structure="FULL_STAR")),
            _answer(MessageEvaluation(specificity=None, logic=None, structure="NONE")),
        ]
    )

    assert "채점 대상 아님 1건" in basis, basis
    assert "감점 사유가 아님" in basis, basis
