import pytest

from ai_server.chain.feedback_generation_chain import (
    EvaluatorResult,
    PanelFeedbackGenerator,
)

# 평가축(dimension_name) 으로 라우팅하는 가짜 체인.
TECH = "기술 정확도·깊이"
PERSONALITY = "인성·협업 역량"
LOGIC = "논리·인과관계 명확성"
COMM = "명료성·구조화·전달력"


class _FakeChain:
    def __init__(self, by_dim: dict[str, EvaluatorResult]):
        self._by_dim = by_dim
        self.calls: list[str] = []

    async def ainvoke(self, variables):
        dim = variables["dimension_name"]
        self.calls.append(dim)
        return self._by_dim[dim]


async def _run(by_dim, **kw):
    gen = PanelFeedbackGenerator(_FakeChain(by_dim))
    return await gen.generate(
        job_category=kw.get("job_category", "BACKEND"),
        mode=kw.get("mode", "TECHNICAL"),
        total_question_count=5,
        end_reason="MAX_QUESTIONS_REACHED",
        transcript="t",
        rag_context="(none)",
        voice_analysis_summary="",
        score_basis="(없음)",
    )


@pytest.mark.asyncio
async def test_weighted_overall_and_dimension_mapping():
    r = await _run(
        {
            TECH: EvaluatorResult(score=80, strength="설계 깊이", keywords=["JPA"]),
            LOGIC: EvaluatorResult(score=60, strength="인과 명확", keywords=["trade-off"]),
            COMM: EvaluatorResult(score=40, strength="간결", keywords=["STAR"]),
        }
    )
    assert r.technical_accuracy == 80
    assert r.logic_score == 60
    assert r.communication_score == 40
    # 0.5*80 + 0.25*60 + 0.25*40 = 65
    assert r.overall_score == 65
    assert "[기술]" in r.strengths_summary and "[논리]" in r.strengths_summary
    assert set(r.improvement_keywords) == {"JPA", "trade-off", "STAR"}


@pytest.mark.asyncio
async def test_overall_reweights_when_a_dimension_is_null():
    r = await _run(
        {
            TECH: EvaluatorResult(score=80),
            LOGIC: EvaluatorResult(score=None),
            COMM: EvaluatorResult(score=40),
        }
    )
    # logic None → (80*0.5 + 40*0.25) / 0.75 = 66.67 → 67
    assert r.logic_score is None
    assert r.overall_score == 67


@pytest.mark.asyncio
async def test_personality_mode_swaps_domain_to_behavioral():
    r = await _run(
        {
            PERSONALITY: EvaluatorResult(score=70, strength="협업 태도 우수"),
            LOGIC: EvaluatorResult(score=50),
            COMM: EvaluatorResult(score=60),
        },
        mode="PERSONALITY",
    )
    # 기술 평가자 자리가 인성·협업 평가자로 교체됨 → technical_accuracy 슬롯에 인성 점수
    assert r.technical_accuracy == 70
    assert "[인성]" in r.strengths_summary


@pytest.mark.asyncio
async def test_keyword_dedup():
    r = await _run(
        {
            TECH: EvaluatorResult(score=70, keywords=["동시성", "트랜잭션"]),
            LOGIC: EvaluatorResult(score=70, keywords=["트랜잭션"]),
            COMM: EvaluatorResult(score=70, keywords=["두괄식"]),
        }
    )
    assert r.improvement_keywords == ["동시성", "트랜잭션", "두괄식"]
