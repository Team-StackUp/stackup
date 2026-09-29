import pytest

from ai_server.chain.feedback_generation_chain import (
    EvaluatorResult,
    PanelFeedbackGenerator,
    SynthesisResult,
    _domain_spec,
    _domain_specs_weighted,
    _EVAL_FAILED_DETAIL,
    _tech_guide_for,
)


class _FakeSynthesis:
    def __init__(self, result: SynthesisResult):
        self._result = result

    async def ainvoke(self, _v):
        return self._result


# 평가축(dimension_name) 으로 라우팅하는 가짜 체인.
TECH = "직무 역량·깊이"
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
    # 평가위원 라벨이 "기술" → 직군명("백엔드")이 됐다. 다직군 경로는 원래 직군명을
    # 썼으므로 이제 단일/다직군이 일관되고, 표시도 더 구체적이다.
    assert "[백엔드]" in r.strengths_summary and "[논리]" in r.strengths_summary
    assert set(r.improvement_keywords) == {"JPA", "trade-off", "STAR"}
    # 평가위원별 분해
    assert [b.evaluator for b in r.panel_breakdown] == ["백엔드", "논리", "전달"]
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

    # 예전엔 "QA" 를 미등록 예시로 썼다 — 이제 실제 직군이라 더는 fallback 이 아니다.
    assert _tech_guide_for("NOT_A_REAL_CATEGORY") == _TECH_GUIDE
    assert _tech_guide_for("") == _TECH_GUIDE
    assert _tech_guide_for(None) == _TECH_GUIDE


def test_generic_guide_is_not_engineering_flavoured():
    """비개발 직군까지 대상이라 기본 관점이 기술 이야기를 하면 안 된다.

    예전 기본값은 "기술 정확성, 깊이, trade-off" 였다. 영업·인사 지원자에게 그 기준을
    들이대면 점수의 근거가 통째로 헛돈다.
    """
    from ai_server.chain.feedback_generation_chain import _TECH_GUIDE

    for word in ("기술", "trade-off"):
        assert word not in _TECH_GUIDE, f"기본 관점에 '{word}' 가 들어 있다"


def test_every_job_category_has_label_and_guide():
    """직군을 늘릴 때 라벨·평가관점 중 하나만 빠뜨리면 그 직군만 조용히 일반 문구를 받는다."""
    from ai_server.chain.feedback_generation_chain import _DOMAIN_KO, _DOMAIN_TECH_GUIDE
    from ai_server.model.messages.job_category import JobCategory
    from typing import get_args

    categories = set(get_args(JobCategory))
    assert categories - set(_DOMAIN_KO) == set(), "한국어 라벨 누락"
    assert categories - set(_DOMAIN_TECH_GUIDE) == set(), "평가 관점 누락"
    assert set(_DOMAIN_KO) - categories == set(), "타입에 없는 라벨이 남아 있다"


def test_literal_matches_db_check_constraint():
    """AI 의 Literal 과 DB CHECK 가 어긋나면 그 직군 세션이 저장되거나 검증되다 터진다.

    직군 목록은 Java enum·마이그레이션·Python Literal·프론트 세 군데에 흩어져 있다.
    사람이 동시에 맞추는 일을 믿지 않는다 — 마이그레이션 원문에서 읽어 대조한다.
    """
    import pathlib
    import re
    from typing import get_args

    from ai_server.model.messages.job_category import JobCategory

    sql = (
        pathlib.Path(__file__).resolve().parents[2]
        / "backend/src/main/resources/db/migration/V35__extend_job_categories.sql"
    ).read_text(encoding="utf-8")
    in_sql = set(re.findall(r"'([A-Z_]+)'", sql))

    assert in_sql == set(get_args(JobCategory)), (
        "Literal 과 마이그레이션 CHECK 가 다르다: "
        f"only-sql={sorted(in_sql - set(get_args(JobCategory)))} "
        f"only-py={sorted(set(get_args(JobCategory)) - in_sql)}"
    )


def test_domain_spec_uses_domain_specific_guide():
    spec = _domain_spec("FRONTEND", "TECHNICAL")
    assert spec.persona == "프론트엔드 직군 시니어 실무 면접관"
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
    be_guide = chain.guides_by_persona["백엔드 직군 시니어 실무 면접관"]
    fe_guide = chain.guides_by_persona["프론트엔드 직군 시니어 실무 면접관"]
    assert be_guide != fe_guide
    assert "트랜잭션" in be_guide
    assert "렌더링" in fe_guide


class _FlakyChain:
    """persona 로 라우팅하되, 지정한 persona 는 예외를 던져 LLM 호출 실패를 흉내낸다."""

    def __init__(self, by_persona: dict[str, EvaluatorResult], fail_personas: set[str]):
        self._by_persona = by_persona
        self._fail = fail_personas

    async def ainvoke(self, v):
        p = v["persona"]
        if p in self._fail:
            raise TimeoutError("upstream timeout")
        return self._by_persona[p]


@pytest.mark.asyncio
async def test_one_evaluator_failure_does_not_break_the_others():
    """평가위원 하나가 예외를 던져도 나머지로 정상 가중평균되고, 실패 축은
    score=None 대신 detail 로 실패 사실이 남아야 한다(회귀 방지)."""
    chain = _FlakyChain(
        {
            "논리·문제해결 평가위원": EvaluatorResult(score=60),
            "커뮤니케이션·전달력 평가위원": EvaluatorResult(score=40),
        },
        fail_personas={"백엔드 직군 시니어 실무 면접관"},
    )
    gen = PanelFeedbackGenerator(chain)
    r = await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=3,
        end_reason="x",
        transcript="t",
        rag_context="(none)",
    )
    # 기술 축은 호출 실패로 점수 없음 → 논리/전달만으로 재가중.
    assert r.technical_accuracy is None
    # (60*0.25 + 40*0.25) / 0.5 = 50
    assert r.overall_score == 50
    tech_item = next(b for b in r.panel_breakdown if b.evaluator == "백엔드")
    assert tech_item.score is None
    assert tech_item.detail == _EVAL_FAILED_DETAIL


@pytest.mark.asyncio
async def test_all_domain_evaluators_failing_still_returns_logic_and_comm():
    chain = _FlakyChain(
        {
            "논리·문제해결 평가위원": EvaluatorResult(score=80),
            "커뮤니케이션·전달력 평가위원": EvaluatorResult(score=70),
        },
        fail_personas={
            "백엔드 직군 시니어 실무 면접관",
            "프론트엔드 직군 시니어 실무 면접관",
        },
    )
    gen = PanelFeedbackGenerator(chain)
    r = await gen.generate(
        job_category="BACKEND",
        mode="TECHNICAL",
        total_question_count=4,
        end_reason="x",
        transcript="t",
        rag_context="(none)",
        domain_question_counts={"BACKEND": 3, "FRONTEND": 1},
    )
    assert r.technical_accuracy is None
    # (80*0.25 + 70*0.25) / 0.5 = 75
    assert r.overall_score == 75
    for label in ("백엔드", "프론트엔드"):
        item = next(b for b in r.panel_breakdown if b.evaluator == label)
        assert item.score is None
        assert item.detail == _EVAL_FAILED_DETAIL


# ── 직군 중립 명명 ───────────────────────────────────────────────────────────
#
# 영업·인사 지원자를 "기술 면접관" 이 "기술 정확도" 로 채점하면 **표시가 아니라 채점이
# 틀어진다** — 페르소나와 평가 축 이름이 프롬프트에 들어가 평가의 틀을 정하기 때문이다.
# 서비스 대상이 취준생 전반이 된 이상 기본 명명은 중립이어야 한다.


def test_persona_and_axis_are_not_engineering_flavoured():
    from ai_server.chain.feedback_generation_chain import _domain_spec
    from ai_server.model.messages.job_category import JobCategory
    from typing import get_args

    for category in get_args(JobCategory):
        spec = _domain_spec(category, "TECHNICAL")
        assert "기술 면접관" not in spec.persona, f"{category}: {spec.persona}"
        assert "기술" not in spec.dimension_name, f"{category}: {spec.dimension_name}"


def test_non_engineering_domain_spec_reads_naturally():
    from ai_server.chain.feedback_generation_chain import _domain_spec

    sales = _domain_spec("SALES", "TECHNICAL")
    assert sales.persona == "영업·영업관리 직군 시니어 실무 면접관"
    assert sales.dimension_name == "직무 역량·깊이"
    # 직군별 실제 관점은 그대로 달라야 한다 — 이름만 중립으로 바꾼 게 아니다.
    assert "고객 문제 파악" in sales.dimension_guide


def test_engineering_domain_still_gets_engineering_guide():
    """중립 명명이 개발 직군의 평가 관점까지 뭉개면 안 된다."""
    from ai_server.chain.feedback_generation_chain import _domain_spec

    backend = _domain_spec("BACKEND", "TECHNICAL")
    assert "트랜잭션" in backend.dimension_guide
