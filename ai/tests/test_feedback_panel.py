import pytest

from ai_server.chain.feedback_generation_chain import (
    EvaluatorResult,
    PanelFeedbackGenerator,
    SynthesisResult,
    _domain_spec,
    _domain_specs_weighted,
    _tech_guide_for,
)


class _FakeSynthesis:
    def __init__(self, result: SynthesisResult):
        self._result = result

    async def ainvoke(self, _v):
        return self._result


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
            LOGIC: EvaluatorResult(
                score=60, strength="인과 명확", keywords=["trade-off"]
            ),
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
    # 평가위원별 분해
    assert [b.evaluator for b in r.panel_breakdown] == ["기술", "논리", "전달"]
    assert [b.score for b in r.panel_breakdown] == [80, 60, 40]


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


class _PersonaChain:
    """persona 내용으로 라우팅(다직군 기술 평가위원은 dimension 이 같아 persona 로 구분)."""

    async def ainvoke(self, v):
        p = v["persona"]
        if "백엔드" in p:
            return EvaluatorResult(score=80, strength="BE 강점")
        if "프론트엔드" in p:
            return EvaluatorResult(score=40, strength="FE 강점")
        if "논리" in p:
            return EvaluatorResult(score=60)
        return EvaluatorResult(score=50)  # 커뮤니케이션


@pytest.mark.asyncio
async def test_multi_domain_weighted_by_question_counts():
    gen = PanelFeedbackGenerator(_PersonaChain())
    r = await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=4,
        end_reason="POOL_EXHAUSTED",
        transcript="t",
        rag_context="(none)",
        domain_question_counts={"BACKEND": 3, "FRONTEND": 1},
    )
    # technical = (80*3 + 40*1)/4 = 70
    assert r.technical_accuracy == 70
    assert r.logic_score == 60
    assert r.communication_score == 50
    # 직군 평가위원 2명 + 논리 + 전달 = 4
    assert [b.evaluator for b in r.panel_breakdown] == [
        "백엔드",
        "프론트엔드",
        "논리",
        "전달",
    ]
    # overall = 0.5*70 + 0.25*60 + 0.25*50 = 62.5 → 62 (은행가 반올림)
    assert r.overall_score == 62


@pytest.mark.asyncio
async def test_synthesis_narrative_study_plan_and_breakdown_detail():
    syn = SynthesisResult(
        strengths_summary="통합 강점 서술",
        weaknesses_summary="통합 약점 서술",
        improvement_keywords=["동시성"],
        study_plan=["Redis 분산 락 SETNX/TTL 직접 구현"],
    )
    gen = PanelFeedbackGenerator(
        _FakeChain(
            {
                TECH: EvaluatorResult(
                    score=80, strength="s", detail="상세 평가", score_rationale="근거"
                ),
                LOGIC: EvaluatorResult(score=60),
                COMM: EvaluatorResult(score=50),
            }
        ),
        synthesis_chain=_FakeSynthesis(syn),
    )
    r = await _run_gen(gen)
    # 종합 서술형(synthesis 결과로 대체)
    assert r.strengths_summary == "통합 강점 서술"
    assert r.weaknesses_summary == "통합 약점 서술"
    assert r.improvement_keywords == ["동시성"]
    assert r.study_plan == ["Redis 분산 락 SETNX/TTL 직접 구현"]
    # 평가위원 분해에 detail/score_rationale 포함
    assert r.panel_breakdown[0].detail == "상세 평가"
    assert r.panel_breakdown[0].score_rationale == "근거"


async def _run_gen(gen):
    return await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=3,
        end_reason="x",
        transcript="t",
        rag_context="(none)",
    )


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


def test_domain_guides_differ_by_job_category():
    """직군마다 실제로 다른 평가 관점(dimension_guide)을 받는지 — 라벨만 다르고
    내용이 같던 회귀를 막는다."""
    frontend = _tech_guide_for("FRONTEND")
    backend = _tech_guide_for("BACKEND")
    infra = _tech_guide_for("INFRA")
    dba = _tech_guide_for("DBA")
    guides = {frontend, backend, infra, dba}
    assert len(guides) == 4  # 넷 다 서로 다른 문구
    assert "렌더링" in frontend
    assert "트랜잭션" in backend
    assert "스케일링" in infra
    assert "실행계획" in dba


def test_unknown_job_category_falls_back_to_generic_guide():
    from ai_server.chain.feedback_generation_chain import _TECH_GUIDE

    assert _tech_guide_for("QA") == _TECH_GUIDE
    assert _tech_guide_for("") == _TECH_GUIDE
    assert _tech_guide_for(None) == _TECH_GUIDE


def test_domain_spec_uses_domain_specific_guide():
    spec = _domain_spec("FRONTEND", "TECHNICAL")
    assert spec.persona == "프론트엔드 직군 시니어 기술 면접관"
    assert "렌더링" in spec.dimension_guide


def test_domain_specs_weighted_gives_each_domain_its_own_guide():
    specs = _domain_specs_weighted(
        "BACKEND", "TECHNICAL", {"BACKEND": 3, "FRONTEND": 1}
    )
    by_label = {spec.label: spec.dimension_guide for spec, _ in specs}
    assert "트랜잭션" in by_label["백엔드"]
    assert "렌더링" in by_label["프론트엔드"]
    assert by_label["백엔드"] != by_label["프론트엔드"]


@pytest.mark.asyncio
async def test_multi_domain_generate_sends_domain_specific_guide_to_chain():
    """generate() 가 각 직군 평가위원 호출에 실제로 다른 dimension_guide 를 넘기는지
    end-to-end 로 확인 (persona 만 다르고 가이드는 공통이던 문제의 회귀 테스트)."""

    class _GuideRecordingChain:
        def __init__(self):
            self.guides_by_persona: dict[str, str] = {}

        async def ainvoke(self, v):
            self.guides_by_persona[v["persona"]] = v["dimension_guide"]
            if "논리" in v["persona"]:
                return EvaluatorResult(score=60)
            if "커뮤니케이션" in v["persona"]:
                return EvaluatorResult(score=50)
            return EvaluatorResult(score=70)

    chain = _GuideRecordingChain()
    gen = PanelFeedbackGenerator(chain)
    await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=4,
        end_reason="POOL_EXHAUSTED",
        transcript="t",
        rag_context="(none)",
        domain_question_counts={"BACKEND": 3, "FRONTEND": 1},
    )
    be_guide = chain.guides_by_persona["백엔드 직군 시니어 기술 면접관"]
    fe_guide = chain.guides_by_persona["프론트엔드 직군 시니어 기술 면접관"]
    assert be_guide != fe_guide
    assert "트랜잭션" in be_guide
    assert "렌더링" in fe_guide
