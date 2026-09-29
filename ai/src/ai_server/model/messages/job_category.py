"""직군 타입의 단일 정의.

전에는 같은 `Literal[...]` 목록이 questions·followup·feedback·voice·tts 5곳에 복사돼
있었다. 직군을 늘릴 때 한 곳이라도 빠뜨리면 **그 메시지만 검증에서 튕기고** 해당 경로가
조용히 DLQ 로 간다 — 다섯 곳을 동시에 맞추는 일에 사람을 믿을 이유가 없다.
"""

from typing import Literal

# Core 의 JobCategory enum 과 1:1. 개발은 세부 직무, 비개발은 대분류 단위다.
JobCategory = Literal[
    # 개발
    "FRONTEND",
    "BACKEND",
    "INFRA",
    "DBA",
    "MOBILE",
    "DATA_AI",
    "SECURITY",
    "QA",
    # 비개발
    "PLANNING",
    "MARKETING",
    "SALES",
    "HR",
    "FINANCE",
    "DESIGN",
    "MANUFACTURING",
    "RND",
    "CUSTOMER_SERVICE",
    "LOGISTICS",
    "LEGAL",
    "PUBLIC",
]


# 개발 직군 집합. 위 목록과 **같은 파일**에 두는 이유는 이 파일의 존재 이유와 같다 —
# 직군을 늘릴 때 목록만 고치고 이 집합을 빠뜨리면, 그 직군은 조용히 비개발로 취급돼
# 기술 질문이 사라지거나 반대로 영업 지원자에게 CS 기초가 나간다(검증에서 튕기지 않으므로
# 아무도 모른다). `tests/test_job_category.py` 가 두 목록의 합집합을 고정한다.
ENGINEERING_CATEGORIES = frozenset(
    {
        "FRONTEND",
        "BACKEND",
        "INFRA",
        "DBA",
        "MOBILE",
        "DATA_AI",
        "SECURITY",
        "QA",
    }
)


def is_engineering(job_category: str | None) -> bool:
    return (job_category or "").upper() in ENGINEERING_CATEGORIES
