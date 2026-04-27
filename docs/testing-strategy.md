# 테스트 전략

> 핵심 시나리오 95% Pass (Product Backlog Success Metric)를 보장하기 위한 테스트 피라미드와 우선순위.

---

## 1. 테스트 피라미드

```
       ┌───────────┐
       │  E2E      │  ← 핵심 시나리오 5~10개 (Playwright)
       └───────────┘
      ┌─────────────┐
      │ Integration │  ← API + DB + MQ 통합 (Testcontainers)
      └─────────────┘
    ┌─────────────────┐
    │     Unit        │  ← 도메인 로직 (가장 많음)
    └─────────────────┘
```

비율 목표: Unit 70% / Integration 25% / E2E 5%.

---

## 2. 레이어별 테스트 도구

| 레이어 | 단위 | 통합 | E2E |
|--------|------|------|-----|
| Backend (Spring Boot) | JUnit 5 + AssertJ + Mockito | Testcontainers (PG, RabbitMQ) | (E2E는 Frontend가 담당) |
| AI Server (FastAPI) | pytest + pytest-asyncio | pytest + httpx + Testcontainers | |
| Frontend (React) | Vitest + Testing Library | MSW (API mock) | Playwright |
| Infra | (script smoke test) | docker compose up + healthcheck | |

---

## 3. 핵심 시나리오 (E2E 대상)

Product Backlog의 Demo Scenario 기반:

| # | 시나리오 | US |
|---|---------|----|
| S1 | GitHub OAuth 로그인 → 신규 사용자 생성 → 프로필 표시 | US-01, US-02 |
| S2 | 동의 → PDF 이력서 업로드 → 분석 완료 SSE 수신 | US-03, US-05, US-09, US-11 |
| S3 | GitHub 레포 등록 → 분석 → analyzed_documents 생성 | US-07, US-10 |
| S4 | 세션 생성(BACKEND/TECHNICAL) → 컨텍스트 연결 → 첫 질문 도착 | US-13, US-14, US-18 |
| S5 | 텍스트 답변 제출 → 꼬리질문 도착 → 세션 종료 → 피드백 생성 | US-19, US-20, US-24 |
| S6 | 세션 히스토리 목록 → 상세 → 점수 추이 차트 | US-15, US-16, US-26 |
| S7 | 회원 탈퇴 → soft delete + refresh revoke | US-04 |

각 시나리오는 Playwright로 작성 (`frontend/e2e/`).

---

## 4. 단위 테스트 우선순위

### Backend
- 도메인 모델의 상태 전이 (`Session.start()`, `.end()`, `.cancel()`)
- 권한 체크 (`canAccess(userId, session)`)
- DTO ↔ Entity 매핑 (특히 enum)
- Util 클래스 (PiiMasker, GithubTokenCipher)

### Frontend
- 커스텀 훅 (`useEventStream`, `useSessionTimer`)
- 유틸 함수 (날짜 포맷, score 계산)
- 복잡한 form validation

### AI Server
- 청킹 로직 (chunk size, overlap)
- 프롬프트 템플릿 렌더링
- 응답 파싱 (LLM JSON output → Pydantic)

---

## 5. 통합 테스트

### Backend (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class SessionApiTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
    @Container static RabbitMQContainer mq = new RabbitMQContainer();

    @Test void createSession_whenAllValid_returns201() {...}
}
```

목표:
- API 계약 (request → response)
- DB 트랜잭션 경계
- RabbitMQ publish 검증 (실제 큐에 메시지 들어가는지)
- 인증·권한 동작

### AI Server
```python
def test_resume_analysis_pipeline(rabbitmq_container, s3_container):
    # publish → consume → S3 결과 검증
```

LLM 호출은 mock (실제 API 호출은 별도 contract test로 분리).

---

## 6. Mock vs Real

| 의존성 | 단위 | 통합 | E2E |
|--------|------|------|-----|
| PostgreSQL | mock (Mockito) | Testcontainer | Testcontainer |
| RabbitMQ | mock | Testcontainer | Testcontainer |
| S3 / MinIO | mock | MinIO container | MinIO container |
| Redis | mock | Testcontainer | Testcontainer |
| GitHub API | mock | mock or sandbox | recorded fixture |
| LLM API | mock | mock (LangChain `FakeListLLM`) | recorded fixture |
| STT/TTS | mock | mock | recorded fixture |

**원칙**: 외부 유료 API는 절대 실제 호출 X (CI 비용·flaky 방지).

---

## 7. 테스트 데이터

### Builder Pattern
```java
public class UserBuilder {
    private Long id = 1L;
    private String githubUsername = "testuser";
    public UserBuilder id(Long id) { this.id = id; return this; }
    public User build() { return new User(...); }
    public static UserBuilder aUser() { return new UserBuilder(); }
}

// 사용
var user = aUser().id(99L).build();
```

### Fixture 파일
- 큰 JSON/PDF는 `src/test/resources/fixtures/`에 보관
- 절대 운영 데이터 사용 X (개인정보 위험)

---

## 8. 커버리지 목표

| 레이어 | line coverage |
|--------|---------------|
| Backend domain/service | ≥ 80% |
| Backend controller | ≥ 70% |
| Frontend hooks/utils | ≥ 70% |
| AI Server core (chain/rag) | ≥ 70% |

> 커버리지는 **참고 지표**. 100% 강요하지 않는다. 의미 없는 테스트로 숫자만 채우는 행동 금지.

---

## 9. Flaky 테스트 정책

- 발견 즉시 `@Disabled` + 이슈 등록
- 1주 내 수정 또는 삭제
- "재실행하면 통과한다"로 넘어가지 않는다

---

## 10. CI 통합

`.github/workflows/lint.yml` 외 추가 권장:
- `ci-backend.yml` — gradlew test (push, PR)
- `ci-frontend.yml` — vitest + lint (push, PR)
- `ci-ai.yml` — pytest + ruff (push, PR)
- `e2e.yml` — Playwright (PR + nightly)

PR은 모든 CI green이어야 머지 가능.

---

## 11. 신규 기능 테스트 체크리스트

PR 머지 전:

- [ ] 행복 경로 (happy path) 단위 테스트
- [ ] 경계값/실패 경로 테스트 (max+1, null, empty)
- [ ] 권한·인증 거부 케이스
- [ ] 외부 의존성 실패 시 동작
- [ ] DB 마이그레이션이 있다면 적용/롤백 검증
- [ ] (UI 변경 시) E2E 시나리오 영향 검토
