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
