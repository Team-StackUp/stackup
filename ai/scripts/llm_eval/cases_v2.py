"""확장 케이스 세트 v2 — 통계적 검정력 확보용 (꼬리질문 40 · 질문 풀 15 · 코칭 15).

v1(cases.py, 22건)은 재현성을 위해 그대로 두고, v1 케이스를 모두 포함한 상위 집합으로 만든다.
검정력 분석(docs/research/thesis-stats): 두 판정자 평균 점수 차이 SD ≈ 0.9~1.1 에서
0.5점 차이를 α=0.05·검정력 0.8 로 검출하려면 꼬리질문 ~37건, 질문 풀·코칭 ~24건이 필요.
(질문 풀·코칭은 1회 호출 비용이 커서 15건으로 두고, 필요 시 반복으로 보완)

모든 데이터는 합성이며 실제 사용자 자료가 아니다.
사용: LLM_EVAL_CASES=cases_v2 python run_eval.py ...
"""

from __future__ import annotations

from cases import (
    COACHING_CASES as V1_COACHING,
    COVER_LETTER,
    FOLLOWUP_CASES as V1_FOLLOWUP,
    QUESTION_CASES as V1_QUESTIONS,
    REPO_FRONTEND,
    RESUME_BACKEND,
    RESUME_INFRA_DBA,
    SELF_INTRO_BACKEND,
)

RESUME_FRONTEND_JUNIOR = """# 이력서 — 박서윤 (프론트엔드, 신입)

## 교육
- 부트캠프 프론트엔드 과정 수료 (2025.09 ~ 2026.02)
- 컴퓨터공학 학사 (2026.02 졸업)

## 프로젝트
### 스터디 모집 플랫폼 (팀 4명, 2026.01)
- Next.js 14 App Router, TypeScript, Tailwind CSS
- 게시글 목록 무한 스크롤 (IntersectionObserver)
- 로그인: NextAuth 카카오 로그인
- Vercel 배포, Lighthouse 접근성 점수 72

### 개인 블로그 (2025.11)
- Gatsby 로 마크다운 블로그 제작, 다크 모드 토글

## 기술
TypeScript, React, Next.js, Tailwind CSS, Git
"""

RESUME_DBA_DATA = """# 이력서 — 정하준 (DBA / 데이터 엔지니어, 5년차)

## 경력
### (주)로지스허브 — DBA (2021.03 ~ 현재)
- MySQL 8.0 (Aurora) 운영: 일 쓰기 2천만 건, 테이블 1.2TB
- 복제 지연 최대 90초 → 3초: 대용량 배치 UPDATE 를 1만 건 단위 청크로 분할, binlog_format ROW 유지
- 온라인 스키마 변경: gh-ost 도입, 컬럼 추가 시 락 대기 장애 0건
- 슬로우 쿼리: pt-query-digest 로 상위 20개 쿼리 선별, 커버링 인덱스 적용
- 백업: 매일 스냅샷 + binlog PITR, 분기별 복구 훈련(RTO 40분)
- 데이터 파이프라인: Debezium CDC → Kafka → BigQuery 적재
"""

JD_BACKEND_FINTECH = """[핀테크 A사] 백엔드 엔지니어 (결제 플랫폼)
주요 업무
- 결제 승인/취소/정산 API 설계 및 운영
- 대용량 트랜잭션 환경에서 데이터 정합성 보장
자격 요건
- Java/Kotlin, Spring 기반 3년 이상
- RDBMS 트랜잭션·격리 수준에 대한 깊은 이해
- 분산 시스템에서의 장애 대응 경험
우대 사항
- Kafka 등 메시지 브로커 운영 경험
- 금융권 보안 규정(전자금융거래법) 이해
- 테스트 자동화, 성능 테스트(nGrinder, k6) 경험
"""

_EXTRA_FOLLOWUP = [
    # --- NORMAL, 강한 답 (high) ---
    {
        "id": "f2-frontend-a11y-strong",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "무한 스크롤을 IntersectionObserver 로 구현하셨는데, 접근성 측면에서 어떤 문제를 고려하셨나요?",
        "expected_signal": "키보드·스크린리더 사용자 문제와 대안(더보기 버튼, aria-live, 포커스 관리) 이해",
        "answer_text": (
            "처음엔 스크롤만 감지해서 키보드로 탭 이동하면 푸터에 절대 못 가는 문제가 있었습니다. 그래서 "
            "20개마다 '더 보기' 버튼을 두는 하이브리드로 바꿨고, 새로 불러온 개수는 aria-live polite 영역으로 "
            "읽어주게 했습니다. 포커스는 새로 추가된 첫 게시글로 옮겼고요. 그 뒤 Lighthouse 접근성이 72에서 "
            "89까지 올랐습니다."
        ),
        "context": RESUME_FRONTEND_JUNIOR,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f2-dba-replication-strong",
        "job_category": "DBA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "복제 지연을 90초에서 3초로 줄이신 과정을 설명해 주세요.",
        "expected_signal": "지연 원인(단일 대형 트랜잭션·단일 스레드 적용) 진단과 청크 분할의 트레이드오프",
        "answer_text": (
            "원인은 야간 배치가 한 트랜잭션으로 수백만 건을 UPDATE 하면서 레플리카가 그 트랜잭션을 통째로 "
            "적용할 때까지 뒤처지는 거였습니다. SHOW REPLICA STATUS 와 binlog 이벤트 크기를 보고 확인했어요. "
            "1만 건 단위로 끊어서 커밋하고 청크 사이에 50ms 쉬게 했더니 최대 지연이 3초로 줄었습니다. 대신 "
            "배치 전체 시간이 20분에서 35분으로 늘었고, 중간 실패 시 재시작 지점을 기록하는 테이블을 따로 뒀습니다."
        ),
        "context": RESUME_DBA_DATA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f2-infra-gitops-strong",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "ArgoCD GitOps 를 도입하면서 기존 배포 방식 대비 무엇이 달라졌나요?",
        "expected_signal": "선언적 상태·드리프트 감지·롤백 방식 차이와 도입 비용",
        "answer_text": (
            "기존엔 젠킨스 파이프라인에서 kubectl apply 를 직접 했는데, 누가 콘솔에서 손으로 바꾼 설정이 "
            "Git 이랑 달라져도 알 수가 없었습니다. ArgoCD 로 바꾸고 나서는 OutOfSync 알림으로 드리프트를 바로 "
            "보고, 롤백은 Git revert 한 번으로 끝났어요. 리드타임이 하루에서 30분으로 줄었고요. 다만 시크릿은 "
            "Git 에 못 넣어서 External Secrets Operator 를 추가로 도입해야 했습니다."
        ),
        "context": RESUME_INFRA_DBA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f2-personality-failure-star",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "실패했던 경험과 그 경험에서 배운 점을 말씀해 주세요.",
        "expected_signal": "본인 책임 인정, 구체적 원인·행동·결과, 이후 달라진 행동",
        "answer_text": (
            "알림 봇을 SQLite 로 만들었다가 동시 쓰기 락 때문에 알림이 중복 발송된 적이 있습니다. 800명한테 같은 "
            "공지가 세 번씩 갔어요. 원인을 찾는 데 3일이 걸렸는데, 제가 로그를 제대로 안 남겨둔 탓이 컸습니다. "
            "PostgreSQL 로 옮기면서 발송 기록에 유니크 제약을 걸었고, 이후엔 새 기능을 만들 때 실패 시나리오와 "
            "로그 설계를 먼저 적어두는 습관이 생겼습니다."
        ),
        "context": COVER_LETTER,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f2-backend-isolation-strong",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "정산 배치에서 트랜잭션 격리 수준은 어떻게 선택하셨나요?",
        "expected_signal": "격리 수준별 이상 현상 이해와 배치 특성에 맞춘 선택 근거",
        "answer_text": (
            "PostgreSQL 기본인 READ COMMITTED 를 썼습니다. 정산은 전날 마감된 주문만 읽어서 배치 도중 값이 바뀌지 "
            "않거든요. REPEATABLE READ 로 올리면 긴 배치에서 스냅샷을 오래 잡아 vacuum 이 밀리는 게 더 문제라고 "
            "봤습니다. 대신 마감 시각 이후 들어온 취소 건은 다음 날 정산에 반영하도록 조회 조건을 created_at "
            "기준으로 고정했습니다."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "null",
    },
    {
        "id": "f2-integrated-tradeoff-strong",
        "job_category": "FRONTEND",
        "mode": "INTEGRATED",
        "parent_category": "TECH_CHOICE",
        "previous_question": "오프라인 편집 충돌을 last-write-wins 로 처리하신 이유가 궁금합니다.",
        "expected_signal": "충돌 빈도·사용자 영향 근거와 CRDT 등 대안 비교",
        "answer_text": (
            "여행 일정은 보통 한 사람이 편집하고 나머지는 보기만 해서, 로그 분석해보니 동시 편집 충돌이 "
            "월 2~3건이었습니다. CRDT 는 Yjs 로 프로토타입을 만들어봤는데 번들이 80KB 늘고 서버 구조도 바꿔야 "
            "해서 비용 대비 효과가 낮다고 판단했어요. 대신 덮어쓰기가 일어나면 이전 버전을 7일간 보관해서 "
            "복구할 수 있게 했습니다."
        ),
        "context": REPO_FRONTEND,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    # --- NORMAL, 약한 답 (low) ---
    {
        "id": "f2-frontend-buzzword-weak",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "Next.js App Router 에서 서버 컴포넌트를 쓰면 어떤 이점이 있나요?",
        "expected_signal": "번들 크기·데이터 패칭 위치·직렬화 경계 등 구체적 이해",
        "answer_text": "서버 컴포넌트는 서버에서 돌아가서 성능이 좋고 SEO 에도 좋습니다. 요즘 트렌드라서 저희도 썼습니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    {
        "id": "f2-dba-vague-weak",
        "job_category": "DBA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "gh-ost 를 도입하신 이유와 운영 중 주의한 점을 말씀해 주세요.",
        "expected_signal": "트리거 없는 방식·컷오버 시 락·부하 제어 등 구체적 운영 포인트",
        "answer_text": "스키마 변경할 때 장애가 안 나게 하려고 도입했고요, 운영할 때는 조심해서 잘 썼습니다.",
        "context": RESUME_DBA_DATA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": None,
    },
    {
        "id": "f2-infra-fact-mismatch",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "RDS 스토리지 풀 장애 이후 어떤 조치를 하셨나요?",
        "expected_signal": "근본 원인과 재발 방지(알람·오토스케일링) 조치의 구체성",
        "answer_text": (
            "그때는 쓰기가 한 5분 정도 멈췄던 것 같고요, 이후에 리전을 이중화해서 다른 리전으로 자동 페일오버되게 "
            "만들었습니다."
        ),
        "context": RESUME_INFRA_DBA,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "low",
    },
    {
        "id": "f2-backend-fact-mismatch",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "정산 배치 성능을 개선하신 방법을 설명해 주세요.",
        "expected_signal": "병목 진단과 각 조치(QueryDSL·청크·인덱스)의 기여 구분",
        "answer_text": (
            "정산 배치는 원래 3시간 걸리던 걸 Spark 로 옮겨서 10분으로 줄였습니다. 분산 처리라서 확실히 빨라졌어요."
        ),
        "context": RESUME_BACKEND,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "low",
    },
    {
        "id": "f2-personality-generic-weak",
        "job_category": "INFRA",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "지원한 직무에서 본인의 강점이 무엇이라고 생각하시나요?",
        "expected_signal": "강점을 뒷받침하는 구체적 경험·수치",
        "answer_text": "저는 책임감이 강하고 성실합니다. 맡은 일은 끝까지 하는 편이고 커뮤니케이션도 잘합니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    {
        "id": "f2-offtopic-weak",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "Kafka 대신 RabbitMQ 를 고려하지 않으신 이유가 있나요?",
        "expected_signal": "처리량·순서 보장·재처리(리플레이) 요구와 브로커 특성 비교",
        "answer_text": (
            "사실 저는 Kafka 를 공부하면서 스트림 처리에 관심이 많아졌고, 요즘은 Flink 도 공부하고 있습니다. "
            "나중에는 실시간 추천 시스템도 만들어보고 싶어요."
        ),
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    # --- DONT_KNOW ---
    {
        "id": "f2-dk-no-experience",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "쿠버네티스에서 PodDisruptionBudget 은 어떤 상황에서 필요한가요?",
        "expected_signal": "자발적 중단(노드 드레인·업그레이드) 시 가용성 보장 이해",
        "answer_text": "그건 제가 직접 써본 경험이 없어서 답변드리기가 어렵습니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-dk-pass",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "브라우저의 렌더링 파이프라인에서 레이아웃과 페인트의 차이를 설명해 주세요.",
        "expected_signal": "리플로우·리페인트·합성 단계 구분",
        "answer_text": "이 질문은 패스하겠습니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-dk-forgot",
        "job_category": "DBA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "분기별 복구 훈련에서 RTO 40분 중 가장 오래 걸린 단계는 무엇이었나요?",
        "expected_signal": "복구 단계별 소요 파악(스냅샷 복원·binlog 재적용·검증)",
        "answer_text": "음… 그게 오래전 일이라 정확히 기억이 안 나네요. 죄송합니다.",
        "context": RESUME_DBA_DATA,
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-dk-english",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "CAP 정리에서 네트워크 분할 시 일관성과 가용성 중 하나를 포기해야 하는 이유는 무엇인가요?",
        "expected_signal": "분할 시 노드 간 합의 불가와 선택의 의미",
        "answer_text": "Sorry, I don't know this one. 잘 모르겠습니다.",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-dk-stt-fragment",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "TECH_CHOICE",
        "previous_question": "Terraform workspace 대신 디렉터리로 환경을 분리하는 방식과 비교하면 어떤가요?",
        "expected_signal": "상태 파일 분리·코드 중복·실수 위험 비교",
        "answer_text": "어… 그 부분은… 음… 잘… 모르겠어요",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "DONT_KNOW",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    # --- CLARIFICATION ---
    {
        "id": "f2-cl-term",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "결제 승인 API 의 멱등성을 HTTP 레벨에서 보장하는 방법을 설명해 주세요.",
        "expected_signal": "Idempotency-Key 헤더·저장·재응답 흐름",
        "answer_text": "죄송한데 여기서 말씀하시는 멱등성이 정확히 어떤 의미인지 먼저 설명해 주실 수 있을까요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-cl-repeat",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "presigned URL 로 S3 에 직접 업로드할 때 클라이언트에서 WebP 로 변환한 이유와 그 한계를 말씀해 주세요.",
        "expected_signal": "서버 부하·비용 절감과 브라우저 호환·원본 손실 한계",
        "answer_text": "네? 죄송합니다, 질문을 한 번만 다시 말씀해 주시겠어요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-cl-example",
        "job_category": "DBA",
        "mode": "TECHNICAL",
        "parent_category": "CS_FUNDAMENTAL",
        "previous_question": "커버링 인덱스가 성능에 도움이 되는 조건을 설명해 주세요.",
        "expected_signal": "인덱스만으로 결과 반환(테이블 접근 생략) 조건 이해",
        "answer_text": "개념이 잘 안 떠오르는데, 예시를 하나 들어서 질문해 주실 수 있을까요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-cl-which-part",
        "job_category": "INFRA",
        "mode": "INTEGRATED",
        "parent_category": "BEHAVIORAL",
        "previous_question": "장애 대응 과정에서 팀과 어떻게 소통했고, 기술적으로는 어떤 조치를 했는지 함께 말씀해 주세요.",
        "expected_signal": "커뮤니케이션 방식과 기술 조치를 모두 구체적으로",
        "answer_text": "질문이 두 가지인 것 같은데요, 소통 방식부터 말씀드리면 될까요, 아니면 기술 조치부터 말씀드릴까요?",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-cl-stt-noisy",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "팀 내에서 기술 부채를 줄이자고 설득했던 경험이 있나요?",
        "expected_signal": "설득 근거·이해관계자 조율·결과",
        "answer_text": "아 잠깐만요 지금 소리가 잘 안 들렸는데 어떤 경험을 말씀하시는 건지 다시 한번 말씀해 주세요",
        "context": "(none)",
        "history": "(none)",
        "expect_intent": "CLARIFICATION",
        "expect_scores": None,
        "expect_correctness": "null",
    },
    {
        "id": "f2-motivation-strong",
        "job_category": "BACKEND",
        "mode": "PERSONALITY",
        "parent_category": "BEHAVIORAL",
        "previous_question": "많은 회사 중에서 결제 플랫폼 팀에 지원하신 이유가 무엇인가요?",
        "expected_signal": "회사·직무와 본인 경험의 구체적 연결, 기여 계획",
        "answer_text": (
            "지난 3년간 주문·결제 도메인에서 중복 주문을 0건으로 만든 경험이 제일 뿌듯했습니다. 그런데 커머스에서는 "
            "결제가 외부 PG 에 기대는 부분이라 정합성을 끝까지 책임지기 어려웠어요. 결제 플랫폼 팀에서는 승인부터 "
            "정산까지 직접 설계할 수 있어서 지원했습니다. 입사하면 멱등 키와 Outbox 를 적용했던 경험으로 취소·환불 "
            "흐름의 중복 처리부터 점검해 보고 싶습니다."
        ),
        "context": RESUME_BACKEND,
        "history": "(none)",
        "expect_intent": "NORMAL",
        "expect_scores": "high",
        "expect_correctness": "high",
    },
    {
        "id": "f2-repeat-history-weak",
        "job_category": "BACKEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "Outbox 릴레이가 이벤트를 두 번 발행했을 때 소비자 쪽에서는 어떻게 처리하셨나요?",
        "expected_signal": "소비자 멱등 처리(이벤트 ID 저장·유니크 제약)와 처리 순서 고려",
        "answer_text": "아까 말씀드린 것처럼 Outbox 를 써서 이벤트 발행이 누락되지 않게 했습니다. 그래서 문제는 없었습니다.",
        "context": "(none)",
        "history": (
            "면접관: 멱등 키와 Outbox 를 함께 쓴 이유는?\n"
            "지원자: 재전송 콜백 중복과 발행 누락을 각각 막으려고 썼습니다. 릴레이가 outbox 테이블을 읽어 발행합니다."
        ),
        "expect_intent": "NORMAL",
        "expect_scores": "low",
        "expect_correctness": "null",
    },
    # --- 확인형 단답 (점수 null) ---
    {
        "id": "f2-confirm-correction",
        "job_category": "INFRA",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "그럼 Terraform 모듈화도 직접 설계하신 건가요?",
        "expected_signal": "(none)",
        "answer_text": "아니요, 모듈 설계는 선배가 했고 저는 환경별 workspace 분리를 맡았습니다.",
        "context": "(none)",
        "history": (
            "면접관: ArgoCD 도입 전후 배포 방식은?\n"
            "지원자: kubectl apply 에서 GitOps 로 바꿔 드리프트를 감지하게 했습니다."
        ),
        "expect_intent": "NORMAL",
        "expect_scores": "null",
        "expect_correctness": "null",
    },
    {
        "id": "f2-confirm-yes",
        "job_category": "FRONTEND",
        "mode": "TECHNICAL",
        "parent_category": "PROJECT_DEEP_DIVE",
        "previous_question": "그럼 카카오 로그인은 NextAuth 의 기본 Provider 를 그대로 쓰신 거죠?",
        "expected_signal": "(none)",
        "answer_text": "네, 그렇습니다.",
        "context": "(none)",
        "history": (
            "면접관: 로그인 기능은 어떻게 구현했나요?\n"
            "지원자: NextAuth 로 카카오 로그인을 붙였고 세션은 JWT 전략을 썼습니다."
        ),
        "expect_intent": "NORMAL",
        "expect_scores": "null",
        "expect_correctness": "null",
    },
]

FOLLOWUP_CASES = list(V1_FOLLOWUP) + _EXTRA_FOLLOWUP

_EXTRA_QUESTIONS = [
    {
        "id": "q2-backend-integrated",
        "job_categories": ["BACKEND"],
        "mode": "INTEGRATED",
        "max_questions": 5,
        "context": RESUME_BACKEND,
        "self_introduction": SELF_INTRO_BACKEND,
    },
    {
        "id": "q2-backend-jd-tailored",
        "job_categories": ["BACKEND"],
        "mode": "JOB_TAILORED",
        "max_questions": 6,
        "context": RESUME_BACKEND,
        "self_introduction": SELF_INTRO_BACKEND,
        "target_company_name": "핀테크 A사",
        "target_job_description": JD_BACKEND_FINTECH,
    },
    {
        "id": "q2-frontend-junior",
        "job_categories": ["FRONTEND"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": RESUME_FRONTEND_JUNIOR,
        "self_introduction": "부트캠프에서 Next.js 로 팀 프로젝트를 했고, 무한 스크롤과 접근성 개선을 담당했습니다.",
    },
    {
        "id": "q2-frontend-personality",
        "job_categories": ["FRONTEND"],
        "mode": "PERSONALITY",
        "max_questions": 5,
        "context": RESUME_FRONTEND_JUNIOR,
        "self_introduction": None,
    },
    {
        "id": "q2-dba-technical",
        "job_categories": ["DBA"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": RESUME_DBA_DATA,
        "self_introduction": None,
    },
    {
        "id": "q2-dba-infra-multi",
        "job_categories": ["DBA", "INFRA"],
        "mode": "TECHNICAL",
        "max_questions": 6,
        "context": RESUME_DBA_DATA + "\n\n" + RESUME_INFRA_DBA,
        "self_introduction": None,
    },
    {
        "id": "q2-infra-recent-dup",
        "job_categories": ["INFRA"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": RESUME_INFRA_DBA,
        "self_introduction": None,
        "recent_questions": [
            "HPA 기준을 CPU 70% 로 잡은 근거는 무엇인가요?",
            "ArgoCD 도입으로 배포 리드타임이 어떻게 줄었나요?",
            "RDS 스토리지 풀 장애 이후 어떤 조치를 했나요?",
        ],
    },
    {
        "id": "q2-backend-focus-weak",
        "job_categories": ["BACKEND"],
        "mode": "TECHNICAL",
        "max_questions": 5,
        "context": RESUME_BACKEND,
        "self_introduction": SELF_INTRO_BACKEND,
        "focus_areas": ["LOGIC", "TECHNICAL"],
    },
    {
        "id": "q2-fullstack-multi",
        "job_categories": ["FRONTEND", "BACKEND"],
        "mode": "INTEGRATED",
        "max_questions": 6,
        "context": REPO_FRONTEND + "\n\n" + RESUME_BACKEND,
        "self_introduction": None,
    },
    {
        "id": "q2-coverletter-integrated",
        "job_categories": ["BACKEND"],
        "mode": "INTEGRATED",
        "max_questions": 5,
        "context": COVER_LETTER,
        "self_introduction": None,
    },
]

QUESTION_CASES = list(V1_QUESTIONS) + _EXTRA_QUESTIONS

# 코칭: v1 3건 + 꼬리질문 케이스에서 파생 12건 (질문·기대신호·답변·자료를 그대로 사용)
_COACH_FROM = [
    "f-strong-backend",
    "f-infra-fact-error",
    "f-personality-star",
    "f-stt-messy-normal",
    "f-english-mixed",
    "f2-frontend-a11y-strong",
    "f2-dba-vague-weak",
    "f2-backend-fact-mismatch",
    "f2-personality-generic-weak",
    "f2-offtopic-weak",
    "f2-dk-no-experience",
    "f2-integrated-tradeoff-strong",
]
_F_BY_ID = {c["id"]: c for c in FOLLOWUP_CASES}
COACHING_CASES = list(V1_COACHING) + [
    {
        "id": "c2-" + fid,
        "job_category": _F_BY_ID[fid]["job_category"],
        "mode": _F_BY_ID[fid]["mode"],
        "target_role": "",
        "question": _F_BY_ID[fid]["previous_question"],
        "expected_signal": _F_BY_ID[fid]["expected_signal"],
        "answer": _F_BY_ID[fid]["answer_text"],
        "rag_context": _F_BY_ID[fid]["context"],
    }
    for fid in _COACH_FROM
]

assert len(FOLLOWUP_CASES) == 40, len(FOLLOWUP_CASES)
assert len(QUESTION_CASES) == 15, len(QUESTION_CASES)
assert len(COACHING_CASES) == 15, len(COACHING_CASES)
assert len({c["id"] for c in FOLLOWUP_CASES}) == 40
