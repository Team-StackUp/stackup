"""비개발 직군 면접이 개발 면접의 복사본이 되지 않아야 한다.

서비스 대상은 취준생 전반(개발 8 + 비개발 12 직군)인데, 평가 패널만 직군별로 확장되고
**질문·꼬리질문·첫인상·문서분석 프롬프트는 "IT 직군" 페르소나로 고정**돼 있었다.
그 상태에서 영업·인사 지원자는 IT 채용 담당자에게 'CS 기초'·'기술 선택' 질문을 받는다.

카테고리 자체가 개발 전용이라는 게 핵심이다 — 문구만 바꾸면 LLM 은 여전히
CS_FUNDAMENTAL 을 고른다.
"""

from __future__ import annotations

import pytest

from ai_server.chain.prompts.document_analysis import SYSTEM_PROMPT as DOC_PROMPT
from ai_server.chain.prompts.followup_generation import (
    SYSTEM_PROMPT as FOLLOWUP_PROMPT,
)
from ai_server.chain.prompts.question_generation import (
    HUMAN_PROMPT,
    SYSTEM_PROMPT as QUESTION_PROMPT,
)
from ai_server.chain.prompts.self_intro_evaluation import (
    SYSTEM_PROMPT as SELF_INTRO_PROMPT,
)
from ai_server.chain.question_generation_chain import _format_job_guide
from ai_server.model.messages.job_category import (
    ENGINEERING_CATEGORIES,
    JobCategory,
    is_engineering,
)
from ai_server.model.messages.questions import QuestionCategory
from typing import get_args


def test_engineering_set_is_a_subset_of_the_job_category_list():
    """두 목록이 어긋나면 그 직군만 조용히 반대 취급을 받는다 — 검증에서 튕기지 않는다."""
    assert ENGINEERING_CATEGORIES <= set(get_args(JobCategory))


def test_every_job_category_is_classified():
    # 새 직군을 넣고 분류를 빠뜨리면 비개발로 떨어진다. 그 자체는 안전한 기본값이지만,
    # 개발 직군을 추가했는데 기술 질문이 사라지는 사고를 막으려면 수를 고정해야 한다.
    assert len(ENGINEERING_CATEGORIES) == 8
    assert len(get_args(JobCategory)) == 20


@pytest.mark.parametrize("cat", ["SALES", "HR", "LEGAL", "MANUFACTURING", "DESIGN"])
def test_non_engineering_guide_forbids_engineering_categories(cat):
    assert not is_engineering(cat)
    guide = _format_job_guide([cat])

    assert "CS_FUNDAMENTAL" in guide and "쓰지 마세요" in guide
    assert "DOMAIN_KNOWLEDGE" in guide
    # 없는 자료를 전제로 묻지 않게 — 비개발 지원자에게 레포는 없다.
    assert "GitHub 레포가 없는 것이 정상" in guide


@pytest.mark.parametrize("cat", ["BACKEND", "FRONTEND", "DATA_AI"])
def test_engineering_guide_keeps_current_behaviour(cat):
    guide = _format_job_guide([cat])

    assert "CS_FUNDAMENTAL" in guide
    assert "쓰지 마세요" not in guide
    assert "DOMAIN_KNOWLEDGE 는 쓰지 않습니다" in guide


def test_mixed_selection_gets_both_rules_not_one():
    # 직군은 복수 선택이 가능하다. 한쪽 규칙만 주면 나머지 직군 질문이 통째로 어긋난다.
    guide = _format_job_guide(["BACKEND", "SALES"])

    assert "질문마다" in guide
    assert "CS_FUNDAMENTAL" in guide and "DOMAIN_KNOWLEDGE" in guide


def test_empty_job_categories_do_not_assume_engineering():
    # 가정이 틀렸을 때 비개발 지원자에게 기술 질문이 나가는 쪽이 반대보다 나쁘다.
    assert "쓰지 마세요" in _format_job_guide([])
    assert "쓰지 마세요" in _format_job_guide(None)


def test_domain_knowledge_is_an_allowed_category():
    # Literal 에 없으면 LLM 이 지침대로 골라도 검증에서 튕겨 생성이 통째로 실패한다.
    assert "DOMAIN_KNOWLEDGE" in get_args(QuestionCategory)


def test_prompts_do_not_hardcode_an_it_persona():
    """직군을 받아 놓고 페르소나만 IT 로 박아 두면 LLM 은 페르소나 쪽으로 기운다."""
    for name, prompt in (
        ("question", QUESTION_PROMPT),
        ("followup", FOLLOWUP_PROMPT),
        ("self_intro", SELF_INTRO_PROMPT),
        ("document_analysis", DOC_PROMPT),
    ):
        assert "IT 직군" not in prompt, f"{name} 프롬프트가 직군을 IT 로 고정한다"


def test_followup_and_self_intro_use_the_job_category_they_receive():
    # 두 프롬프트는 예전부터 {job_category} 를 받고 있었는데 쓰지 않았다.
    assert "{job_category} 직군" in FOLLOWUP_PROMPT
    assert "{job_category} 직군" in SELF_INTRO_PROMPT


def test_question_prompt_injects_the_job_guide():
    assert "{job_guide}" in HUMAN_PROMPT
