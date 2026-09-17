"""LLM 후보 평가용 라벨링 케이스 세트 (합성 데이터 — 실제 사용자 데이터 아님).

케이스 설계 근거 (운영 ai_request_logs / interview_messages 집계, 2026-09-17):
- 질문 풀 입력 토큰 p50 3,141 / p90 4,722 / max 4,869 (Gemini 토크나이저 기준)
- 지원자 답변 길이 p50 162자 / p90 425자 / max 2,583자
- 피드백 코칭은 세션당 ~15건 fan-out, 동시성 5
"""

from __future__ import annotations

RESUME_BACKEND = """# 이력서 — 김도현 (백엔드 개발자, 3년차)

## 요약
- Spring Boot / Kotlin 기반 커머스 주문·정산 도메인 3년
- MSA 전환 프로젝트에서 주문 서비스 분리 및 Kafka 이벤트 파이프라인 설계
- 장애 대응: 결제 승인 지연으로 인한 중복 주문 이슈를 멱등 키 + Outbox 패턴으로 해결

## 경력
### (주)마켓온 — 백엔드 개발자 (2023.03 ~ 현재)
- 주문/결제 도메인 담당. 일 평균 주문 12만 건 처리
- 모놀리식 → MSA 전환: 주문 서비스를 별도 서비스로 분리, DB 분리(PostgreSQL) 및
  Kafka 기반 이벤트 발행/구독 구조 도입. 배포 단위 축소로 배포 주기 2주 → 2일
- 정산 배치 성능 개선: 5시간 → 40분 (QueryDSL 튜닝, 청크 단위 처리, 인덱스 재설계)
- 결제 승인 콜백 지연 시 중복 주문 발생 문제: 멱등 키 테이블 + Transactional Outbox 로
  해결, 중복 주문 0건 달성

### 스타트업 인턴 — 서버 개발 (2022.07 ~ 2022.12)
- Node.js/Express 기반 사내 예약 시스템 API 개발, Jest 테스트 커버리지 70% 달성

## 프로젝트
### 실시간 재고 동기화 (2024)
- Redis 기반 재고 캐시 + DB write-behind, 동시성 제어(낙관적 락 → 분산 락 전환)
- 재고 불일치 건수 월 200건 → 3건

## 기술 스택
Kotlin, Java 17, Spring Boot 3, JPA/QueryDSL, PostgreSQL, Kafka, Redis, Docker, GitHub Actions
"""

REPO_FRONTEND = """# GitHub 레포 분석 — park-sy/travel-planner (React 18 + TypeScript)

## 개요
여행 일정 공유 웹앱. 월간 활성 사용자 약 3천 명(README 기준). Vite + React 18 + TypeScript,
상태 관리는 Zustand, 서버 상태는 TanStack Query v5.

## 핵심 구현
- 일정 타임라인: 항목 2천 개 이상일 때 스크롤 끊김 → react-window 가상화 도입
  (커밋 메시지: "virtualize timeline, INP 480ms -> 120ms")
- 지도 마커 렌더링: Kakao Map SDK 마커 500개를 클러스터링, useMemo 로 좌표 변환 캐싱
- 오프라인 편집: IndexedDB(Dexie) 에 낙관적 업데이트를 저장 후 온라인 복귀 시 동기화.
  충돌은 last-write-wins (TODO 주석: "CRDT 검토 필요")
- 이미지 업로드: presigned URL 로 S3 직접 업로드, 클라이언트에서 WebP 변환

## 테스트/품질
- Vitest 단위 테스트 42개, Playwright E2E 6개
- Lighthouse 성능 점수 68 → 91 (PR #57 설명)

## 기술 스택
React 18, TypeScript, Vite, Zustand, TanStack Query, react-window, Dexie, Vitest, Playwright
"""

RESUME_INFRA_DBA = """# 이력서 — 이서연 (인프라/DBA, 4년차)

## 경력
### (주)핀링크 — 플랫폼 엔지니어 (2022.01 ~ 현재)
- EKS 기반 쿠버네티스 클러스터 3개 운영 (파드 약 400개)
- HPA 기준을 CPU 70% 로 설정, 트래픽 피크(월급날 10시) 대비 사전 스케일아웃 CronJob 운영
- Terraform 으로 VPC·RDS·EKS 모듈화, 환경별(dev/stg/prod) workspace 분리
- ArgoCD GitOps 도입으로 배포 리드타임 1일 → 30분
- PostgreSQL 14 (RDS) 운영: 슬로우 쿼리 p95 2.3초 → 180ms
  (복합 인덱스 재설계, autovacuum 튜닝, 파티셔닝으로 거래내역 테이블 월 단위 분할)
- 장애: 2024.03 RDS 스토리지 풀로 쓰기 중단 40분 → CloudWatch 알람 + 스토리지 오토스케일링 적용

## 자격/기술
CKA, AWS SAA / Kubernetes, EKS, Terraform, ArgoCD, Prometheus, Grafana, PostgreSQL, Redis
"""

COVER_LETTER = """# 자기소개서 — 최민준 (백엔드 지원)

## 지원동기
학부 동아리에서 학사 공지 알림 봇을 만들며 사용자 800명이 매일 쓰는 서비스를 운영해 본 경험이
있습니다. 새벽에 크롤러가 멈춰 공지가 누락됐을 때 동아리원들의 항의를 직접 받으며, 기능보다
'멈추지 않는 서비스'가 신뢰를 만든다는 것을 배웠습니다.

## 협업 경험
캡스톤 프로젝트에서 프론트엔드 팀원과 API 스펙 해석이 달라 통합 직전 2주가 지연된 적이 있습니다.
저는 OpenAPI 명세를 먼저 작성하고 Mock 서버를 띄워 프론트가 병렬로 개발하도록 제안했고,
이후 스프린트에서는 통합 이슈가 3건에서 0건으로 줄었습니다. 다만 초반에 제 방식이 옳다고
고집해 팀원과 감정이 상한 적도 있어, 이후에는 결정 전에 대안 두 가지를 함께 비교하는 방식으로
바꿨습니다.

## 실패 경험
알림 봇 DB를 SQLite 로 시작했다가 동시 쓰기 락 문제로 알림이 중복 발송되었습니다.
원인을 찾는 데 3일이 걸렸고, PostgreSQL 로 이전하며 트랜잭션 격리 수준을 공부하게 됐습니다.
"""

SELF_INTRO_BACKEND = (
    "안녕하세요, 커머스 주문 결제 도메인에서 3년 동안 백엔드를 개발한 김도현입니다. "
    "모놀리식을 MSA로 전환하면서 주문 서비스를 분리했고, 결제 콜백 지연으로 생긴 중복 주문 문제를 "
    "멱등 키와 Outbox 패턴으로 해결한 경험이 가장 기억에 남습니다. 대용량 트래픽에서도 데이터 "
    "정합성을 지키는 서버를 만드는 데 관심이 많습니다."
)


def _long_context() -> str:
    """운영 p90(Gemini ~4.7k 토큰) 수준의 다문서 컨텍스트."""
    return "\n\n".join(
        [
            RESUME_BACKEND,
            RESUME_BACKEND.replace("김도현", "김도현(경력기술서 상세)").replace(
                "## 기술 스택",
                "## 상세 회고\n- 각 프로젝트의 의사결정 배경과 대안 비교는 면접에서 설명 가능\n## 기술 스택",
            ),
            REPO_FRONTEND.replace("park-sy/travel-planner", "kim-dh/order-admin")
            .replace("여행 일정 공유 웹앱", "주문 관리 어드민")
            .replace("React 18", "React 18 (사이드 프로젝트)"),
            COVER_LETTER.replace("최민준", "김도현"),
        ]
    )


# ---------------------------------------------------------------------------
# 질문 풀 생성 (Pro 티어) 케이스
# ---------------------------------------------------------------------------
QUESTION_CASES = [
    {
        "id": "q-backend-tech",
        "job_categories": ["BACKEND"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": RESUME_BACKEND,
        "self_introduction": SELF_INTRO_BACKEND,
    },
    {
        "id": "q-frontend-repo",
        "job_categories": ["FRONTEND"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": REPO_FRONTEND,
        "self_introduction": None,
    },
    {
        "id": "q-infra-dba-multi",
        "job_categories": ["INFRA", "DBA"],
        "mode": "INTEGRATED",
        "max_questions": 6,
        "context": RESUME_INFRA_DBA,
        "self_introduction": None,
    },
    {
        "id": "q-personality-coverletter",
        "job_categories": ["BACKEND"],
        "mode": "PERSONALITY",
        "max_questions": 5,
        "context": COVER_LETTER,
        "self_introduction": None,
    },
    {
        "id": "q-long-context-p90",
        "job_categories": ["BACKEND"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": _long_context(),
        "self_introduction": SELF_INTRO_BACKEND,
        "recent_questions": [
            "결제 승인 콜백 지연으로 인한 중복 주문을 멱등 키와 Outbox 로 해결한 과정을 설명해 주세요.",
            "정산 배치를 5시간에서 40분으로 줄인 방법은 무엇인가요?",
        ],
    },
]

# ---------------------------------------------------------------------------
# 꼬리질문 (Flash 티어, 스트리밍 태그 출력) 케이스 — 라벨 포함
#   expect_intent: 정답 의도
#   expect_scores: "null" (확인형 단답/모름) | "low" (spec<=2) | "high" (spec>=3) | None(무관)
#   expect_correctness: "null" (컨텍스트 없음) | "low" (<=2, 사실 불일치) | "high" (>=3) | None
# ---------------------------------------------------------------------------
_Q_OUTBOX = (
    "결제 승인 콜백 지연으로 중복 주문이 발생했던 문제를 멱등 키와 Outbox 패턴으로 해결하셨다고 "
    "했는데, 두 가지를 함께 쓴 이유와 각각이 어떤 실패 케이스를 막는지 설명해 주세요."
)
_SIG_OUTBOX = "멱등성과 트랜잭션 경계에 대한 이해, 실패 케이스를 구체적으로 구분하는지"

FOLLOWUP_CASES = [
    {
        "id": "f-strong-backend",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": _Q_OUTBOX,
        "expected_signal": _SIG_OUTBOX,
        "answer_text": (
            "멱등 키는 같은 결제 승인 콜백이 두 번 들어와도 주문이 한 번만 생성되게 하려고 썼습니다. "
            "PG사가 타임아웃 후 재전송하면서 같은 승인 건이 두 번 오는 경우가 있었고, 결제 키에 유니크 "
            "제약을 걸어 두 번째 요청은 기존 주문을 그대로 돌려주도록 했습니다. Outbox 는 주문 저장과 "
            "Kafka 발행이 한 트랜잭션이 아니어서 주문은 저장됐는데 이벤트가 안 나가는 경우를 막으려고 "
            "도입했습니다. 이벤트를 같은 DB 트랜잭션에서 outbox 테이블에 쓰고 릴레이가 폴링해 발행합니다. "
            "도입 후 중복 주문은 월 30건에서 0건이 됐습니다."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
    {
        "id": "f-weak-vague",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": _Q_OUTBOX,
        "expected_signal": _SIG_OUTBOX,
        "answer_text": "그냥 중복이 안 생기게 잘 처리했고요, 트랜잭션도 잘 관리해서 문제없이 해결됐습니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    {
        "id": "f-dont-know-explicit",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "PostgreSQL 의 MVCC 에서 오래 열린 트랜잭션이 vacuum 에 어떤 영향을 주는지 설명해 주세요.",
        "expected_signal": "xmin horizon 과 dead tuple 회수 지연, 테이블 bloat 연결",
        "answer_text": "음... 그 부분은 솔직히 잘 모르겠습니다. 다음 질문으로 넘어가도 될까요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f-dont-know-stt",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "React 18 의 automatic batching 이 이전 버전과 어떻게 다른지 설명해 주세요.",
        "expected_signal": "setTimeout/Promise 등 비동기 콜백 내부 업데이트도 배칭된다는 점",
        "answer_text": "어 그 음 배칭이요 그거는 어 제가 공부를 안 해서 어 모르겠어요 죄송합니다",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f-clarification",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "HPA 기준을 CPU 70% 로 잡으신 근거와, 트래픽 피크에 사전 스케일아웃을 병행한 이유를 설명해 주세요.",
        "expected_signal": "HPA 반응 지연(메트릭 수집·파드 기동 시간)과 예측 가능한 피크의 구분",
        "answer_text": "죄송한데 질문이 조금 길어서요, 무엇을 여쭤보시는 건지 좀 더 쉽게 다시 말씀해 주실 수 있을까요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f-confirm-short",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "그럼 outbox 릴레이는 별도 프로세스로 폴링하신 건가요?",
        "expected_signal": "(none)",
        "answer_text": "네, 맞습니다.",
        "context": "(none)",
        "history": (
            "면접관: 멱등 키와 Outbox 를 함께 쓴 이유는?\n"
            "지원자: 재전송 콜백 중복과 발행 누락을 각각 막으려고 썼습니다. 릴레이가 outbox 테이블을 읽어 발행합니다."
        ),
        "expect_intent": "NORMAL",
        "expect_scores": "null",
        "expect_correctness": "null",
    },
    {
        "id": "f-infra-fact-error",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "HPA 기준과 트래픽 피크 대응 방식을 어떻게 설계하셨는지 설명해 주세요.",
        "expected_signal": "HPA 메트릭 선택 근거와 사전 스케일아웃 병행 이유",
        "answer_text": (
            "HPA 는 메모리 50% 를 기준으로 잡았습니다. 저희 서비스는 메모리를 많이 써서요. 피크 대응은 "
            "따로 한 건 없고 HPA 가 알아서 늘려줬습니다."
        ),
        "context": RESUME_INFRA_DBA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "low",
    },
    {
        "id": "f-dba-correct-with-context",
        "job_category": "DBA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "슬로우 쿼리 p95 를 2.3초에서 180ms 로 줄인 과정을 설명해 주세요.",
        "expected_signal": "원인 진단 방법(EXPLAIN 등)과 인덱스·파티셔닝·vacuum 각각의 기여 구분",
        "answer_text": (
            "먼저 pg_stat_statements 로 상위 쿼리를 뽑고 EXPLAIN ANALYZE 로 보니 거래내역 조회가 "
            "seq scan 을 타고 있었습니다. (user_id, created_at) 복합 인덱스로 바꾸고, 거래내역 테이블을 "
            "월 단위로 파티셔닝해서 최근 3개월 조회는 파티션 프루닝이 되게 했습니다. autovacuum 은 "
            "scale_factor 를 0.2에서 0.05로 낮춰 dead tuple 이 쌓이지 않게 했고요. 인덱스 변경만으로 "
            "p95 가 900ms 까지 내려갔고 파티셔닝 후 180ms 가 됐습니다."
        ),
        "context": RESUME_INFRA_DBA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f-frontend-strong",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "타임라인 스크롤 끊김을 react-window 가상화로 해결하신 과정을 설명해 주세요.",
        "expected_signal": "병목 측정 방법과 가상화의 트레이드오프(동적 높이, 접근성, 검색)",
        "answer_text": (
            "React DevTools Profiler 로 보니 항목 2천 개가 전부 리렌더링되면서 INP 가 480ms 까지 "
            "나왔습니다. react-window 의 VariableSizeList 로 화면에 보이는 30개 정도만 렌더하게 했고 "
            "INP 가 120ms 로 줄었습니다. 대신 항목 높이가 내용에 따라 달라서 높이 캐시를 따로 뒀고, "
            "브라우저 Ctrl+F 검색이 안 되는 문제는 자체 검색 박스로 대체했습니다."
        ),
        "context": REPO_FRONTEND,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f-personality-star",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "팀원과 의견이 충돌했던 경험과 그것을 어떻게 해결했는지 말씀해 주세요.",
        "expected_signal": "본인의 구체적 행동과 정량적 결과, 이후 달라진 점(STAR)",
        "answer_text": (
            "캡스톤에서 프론트 팀원과 API 스펙 해석이 달라 통합 직전에 2주가 밀렸습니다. 제가 맡은 건 "
            "통합 일정을 되돌리는 거였고요. 그래서 OpenAPI 명세를 먼저 쓰고 Mock 서버를 띄워 프론트가 "
            "병렬로 개발하게 제안했습니다. 다음 스프린트부터 통합 이슈가 3건에서 0건이 됐습니다. 다만 "
            "처음엔 제 방식만 고집해서 팀원과 감정이 상했고, 그 뒤로는 결정 전에 대안 두 개를 같이 "
            "비교하는 식으로 바꿨습니다."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
    {
        "id": "f-personality-rambling",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "팀원과 의견이 충돌했던 경험과 그것을 어떻게 해결했는지 말씀해 주세요.",
        "expected_signal": "본인의 구체적 행동과 정량적 결과, 이후 달라진 점(STAR)",
        "answer_text": (
            "저는 원래 사람들이랑 잘 지내는 편이라서 크게 싸운 적은 없는 것 같고요, 그래도 의견이 다를 "
            "때는 서로 대화를 많이 하는 게 중요하다고 생각합니다. 소통이 제일 중요하니까요. 팀워크가 "
            "좋으면 결과도 좋게 나온다고 봅니다."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    {
        "id": "f-stt-messy-normal",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "재고 동시성 제어를 낙관적 락에서 분산 락으로 바꾼 이유가 무엇인가요?",
        "expected_signal": "충돌 빈도·재시도 비용과 락 방식 트레이드오프 이해",
        "answer_text": (
            "어 그러니까 음 처음에는 낙관적 락으로 했는데요 어 타임세일 때 같은 상품에 요청이 한 번에 "
            "몰리니까 버전 충돌이 너무 많이 나서 재시도가 막 계속 돌았어요 음 그래서 재시도 때문에 오히려 "
            "DB 부하가 커져서 어 레디스 분산 락으로 바꿨고 그 다음에 불일치가 월 200건에서 3건으로 줄었습니다"
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
    {
        "id": "f-long-answer-history",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "MSA 전환에서 주문 서비스의 DB 를 분리할 때 데이터 정합성은 어떻게 보장하셨나요?",
        "expected_signal": "분산 트랜잭션 대안(Saga/이벤트)과 보상 처리, 조회 모델 분리",
        "answer_text": (
            "주문 DB 를 분리하면서 가장 걱정했던 게 주문과 결제, 재고가 서로 다른 DB 에 있게 되니까 기존처럼 "
            "하나의 트랜잭션으로 묶을 수가 없다는 점이었습니다. 2PC 도 검토했는데 코디네이터 장애 시 전체가 "
            "막히고 Kafka 와도 잘 맞지 않아서 제외했습니다. 그래서 코레오그래피 방식의 Saga 로 갔습니다. 주문이 "
            "생성되면 OrderCreated 이벤트를 outbox 로 발행하고, 결제 서비스가 소비해서 승인하면 PaymentApproved, "
            "실패하면 PaymentFailed 를 발행합니다. 재고 서비스는 PaymentApproved 를 받아 차감하고, 재고가 부족하면 "
            "StockReserveFailed 를 내보내서 결제 서비스가 취소를 하고 주문 서비스가 주문 상태를 CANCELLED 로 "
            "바꾸는 보상 흐름을 만들었습니다. 각 소비자는 이벤트 ID 로 멱등 처리를 했고요. 운영하면서 문제가 됐던 "
            "건 보상 이벤트가 유실되면 주문이 PENDING 에 계속 남는 경우였는데, 30분 이상 PENDING 인 주문을 찾아 "
            "상태를 재조회하는 스위퍼 배치를 붙여서 해결했습니다. 조회 쪽은 주문 목록 화면이 결제 상태까지 "
            "보여줘야 해서, 이벤트를 받아 만드는 읽기 전용 뷰 테이블을 따로 두었습니다. 이 구조로 전환한 뒤 "
            "정합성 불일치 신고는 분기에 한두 건 수준으로 유지되고 있습니다."
        ),
        "context": "(none)",
        "history": (
            "면접관: 모놀리식에서 MSA 로 전환하게 된 계기는?\n"
            "지원자: 배포 주기가 2주였고 주문 쪽 변경이 전체 배포를 막아서 주문 서비스부터 분리했습니다.\n"
            "면접관: 서비스 간 통신은 동기와 비동기 중 무엇을 택했나요?\n"
            "지원자: 주문-결제-재고 흐름은 Kafka 비동기, 조회성 호출만 REST 동기로 했습니다."
        ),
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
    {
        "id": "f-english-mixed",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "서버 상태 관리에 TanStack Query 를 선택한 이유는 무엇인가요?",
        "expected_signal": "캐싱·stale 관리·중복 요청 제거 등 서버 상태 특성과 클라이언트 상태 구분",
        "answer_text": (
            "Zustand 로 fetch 결과까지 들고 있으니까 cache invalidation 을 직접 짜야 했고 stale data 버그가 "
            "자주 났습니다. TanStack Query 로 옮기면서 staleTime 을 화면별로 다르게 주고, mutation 후에 "
            "invalidateQueries 로 목록만 갱신했습니다. 같은 query key 요청이 dedupe 되니까 네트워크 요청도 "
            "40% 정도 줄었습니다."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
]

# ---------------------------------------------------------------------------
# 답변 코칭 (Flash 티어, 피드백 fan-out) 케이스
# ---------------------------------------------------------------------------
COACHING_CASES = [
    {
        "id": "c-weak-backend",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "target_role": "",
        "question": _Q_OUTBOX,
        "expected_signal": _SIG_OUTBOX,
        "answer": FOLLOWUP_CASES[1]["answer_text"],
        "rag_context": RESUME_BACKEND,
    },
    {
        "id": "c-dont-know-cs",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "target_role": "",
        "question": FOLLOWUP_CASES[2]["previous_question"],
        "expected_signal": FOLLOWUP_CASES[2]["expected_signal"],
        "answer": FOLLOWUP_CASES[2]["answer_text"],
        "rag_context": "(none)",
    },
    {
        "id": "c-personality-rambling",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "target_role": "",
        "question": FOLLOWUP_CASES[10]["previous_question"],
        "expected_signal": FOLLOWUP_CASES[10]["expected_signal"],
        "answer": FOLLOWUP_CASES[10]["answer_text"],
        "rag_context": COVER_LETTER,
    },
]
