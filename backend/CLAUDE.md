# Backend (Core Server) — Claude 컨텍스트

> StackUp Core Server. **Java 21 + Spring Boot 4.0 + JPA + QueryDSL + PostgreSQL**. 시스템에서 PostgreSQL에 직접 접근하는 **유일한 컴포넌트**.

상위 컨텍스트: [`/CLAUDE.md`](../CLAUDE.md) · 횡단 관심사: [`/docs/`](../docs/README.md)

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Java 21 (toolchain) |
| Framework | Spring Boot 4.0.4 |
| Web | Spring Web (REST) |
| ORM | Spring Data JPA + Hibernate |
| Query | QueryDSL 5.1 (jakarta classifier) |
| DB | PostgreSQL + pgvector |
| Build | Gradle (Groovy DSL) |
| Test | JUnit 5 + Spring Boot Test |
| 추가 예정 | Spring Security, RabbitMQ starter, Flyway, springdoc-openapi, **ArchUnit** (의존성 검증) |

> 신규 의존성 추가 시 [`/docs/coding-conventions.md §6`](../docs/coding-conventions.md) 절차 + `build.gradle` 갱신.

---

## 2. 패키지 구조 (도메인 우선)

```
com.stackup.stackup
├── StackupApplication.java
├── auth/                       # 인증 (GitHub OAuth, JWT)
│   └── domain/
├── user/                       # 사용자
│   └── domain/
│       └── consent/            # 개인정보처리동의
├── github/                     # GitHub API 연동·레포 메타
│   └── domain/
├── resume/                     # 이력서
│   └── domain/
├── document/                   # 분석 문서 (analyzed_documents)
│   └── domain/
├── session/                    # 면접 세션·메시지·피드백
│   └── domain/
├── log/                        # 로깅 도메인
│   ├── activity/
│   │   └── domain/
│   └── ai/
│       └── domain/
└── common/                     # 횡단 (Base entity, exceptions, util)
    └── entity/
```

> Spring Boot 표준 (`config/`, `controller/`, `service/`, `repository/`, `dto/`)이 아니라 **도메인 패키지 우선** 구조. 각 도메인 내부에서 layered 분리한다 (§3).

---

## 3. 도메인 내부 구조 (각 패키지의 표준)

```
com.stackup.stackup.{domain}/
├── domain/                # Entity, Enum, Value Object, Repository (interface)
│   ├── {Aggregate}.java
│   ├── {Aggregate}Repository.java
│   └── ...
├── application/           # Service, UseCase, DTO (도입 예정)
│   ├── {Aggregate}Service.java
│   └── dto/
├── presentation/          # Controller, Request/Response (도입 예정)
│   └── {Aggregate}Controller.java
└── infrastructure/        # 외부 연동 (GitHub API client, S3, RabbitMQ pub/sub)
    └── ...
```

- 현재는 `domain/` 하위 패키지만 존재. 기능 구현 시 `application/`, `presentation/`, `infrastructure/` 차례로 추가.
- 패키지명을 entity 명사로 (소문자), 클래스는 PascalCase.

---

## 4. 도메인 인벤토리

| 패키지 | 책임 | 관련 US |
|--------|------|---------|
| `auth` | OAuth flow (GitHub · Google), JWT 발급/갱신/검증, refresh token, JWT 필터 | US-01 |
| `user` | 사용자 CRUD, 회원 탈퇴, 프로필 조회 | US-02, US-04 |
| `user.consent` | 개인정보처리동의 기록·조회·철회 | US-03 |
| `github` | GitHub API 연동, 레포 목록/등록/메타 동기화 | US-07, US-08 |
| `resume` | 이력서 업로드(S3)·메타 저장·목록·삭제 + **웹 이력서(URL) 등록**(`file_type=WEB`, `source_url`; SSRF 가드 `WebResumeUrlValidator`) | US-05, US-06, US-09 |
| `coverletter` | 자소서(공채) 문항별 텍스트 입력·메타 저장·목록·삭제. inline 텍스트→`analyze.cover_letter`→분석 파이프라인 재사용. AnalyzedDocument 에 `cover_letter_id` 다형성 FK 추가 | — |
| `document` | 분석 문서(이력서/레포/자소서 공통) 메타 + S3 경로 | US-09~12 |
| `session` | 면접 세션·메시지·피드백 (가장 큰 도메인) | US-13~20, US-24~27 |
| `log.activity` | 사용자 행동 로그 | US-31 |
| `log.ai` | AI 요청/응답 로깅 | US-30 |
| `common` | BaseEntity, 글로벌 예외 핸들러, util | — |

각 도메인 패키지에 자체 `CLAUDE.md`를 두는 것을 권장 (현재 미생성, 도메인 코드 작성 시 함께 추가).

---

## 5. 핵심 설계 원칙

### 5.1 PostgreSQL 단독 접근
다른 서비스(AI/RealTime)는 PG 직접 접근 금지. 본 서버 API 또는 RabbitMQ 경유. 자세한 이유는 [`/docs/architecture.md §4.1`](../docs/architecture.md).

### 5.2 도메인 패키지 우선
횡단 기술(controller/service/repository) 분리 대신 **도메인 단위 응집**. 도메인 내부에서만 layered 분리.

### 5.3 비동기 작업 발행만, AI 추론은 AI 서버 위임
LLM 직접 호출 X. 항상 RabbitMQ로 작업 발행 + 콜백 수신.

### 5.4 트랜잭션 경계
- `@Transactional`은 service layer에서만 (controller/repository에서 사용 X)
- 외부 API 호출은 트랜잭션 밖에서 (DB 락 길어짐 방지)
- 메시지 발행은 commit 이후 (transaction outbox 패턴 또는 `TransactionalEventListener(AFTER_COMMIT)`)

---

## 6. JPA / QueryDSL 가이드

- 단순 CRUD → `JpaRepository` 메서드 (`findById`, `findByUserIdAndIsDeletedFalse`)
- 동적 조건/조인 → QueryDSL custom repository
- N+1 방지: `@EntityGraph` 또는 fetch join, 측정 후 적용
- `@OneToMany` cascade는 신중 (의도 없는 삭제 방지)
- 비식별자 ENUM은 `@Enumerated(EnumType.STRING)` 강제, ORDINAL 금지

QueryDSL Q-class 생성 위치: `build/generated/sources/annotationProcessor/...` (자동, 커밋 X).

---

## 7. DTO 컨벤션

- 입력: `XxxRequest` (record 권장)
- 출력: `XxxResponse` (record 권장)
- Entity는 controller까지 노출 X — service에서 DTO 변환
- `@Valid` + `@NotBlank` 등 validation은 Request DTO에

```java
public record SessionCreateRequest(
    String title,
    @NotNull SessionMode mode,
    @NotNull JobCategory jobCategory,
    @Min(2) @Max(30) Integer maxQuestions,
    @Min(5) @Max(180) Integer maxDurationMinutes,
    List<Long> contextDocumentIds
) {}
```

---

## 8. 예외 처리

### 8.1 도메인 예외
```java
public class SessionNotInProgressException extends DomainException {
    public SessionNotInProgressException(Long sessionId) {
        super(ApiErrorCode.SESSION_INVALID_STATE,
              "세션이 진행 중이 아닙니다. (id=%d)".formatted(sessionId));
    }
}
```

### 8.2 글로벌 핸들러
`common/exception/GlobalExceptionHandler.java` 에서 `@RestControllerAdvice`로:
- `DomainException` → 4xx + 표준 에러 응답 ([`/docs/api-conventions.md §4.2`](../docs/api-conventions.md))
- `MethodArgumentNotValidException` → 400 + details에 field 목록
- 그 외 `Exception` → 500 + traceId 노출 (사용자에게 메시지 노출은 generic하게)

---

## 9. RabbitMQ 발행/소비

- 발행자: 각 도메인의 `infrastructure/` (예: `session/infrastructure/SessionEventPublisher.java`)
- 소비자: `*/infrastructure/{X}MessageHandler.java`
- 메시지 envelope·routing key·재시도 정책: [`/docs/messaging.md`](../docs/messaging.md)

---

## 10. S3 / MinIO

- `common/storage/ObjectStorageClient.java` 단일 추상화
- AWS SDK v2 사용, endpoint를 환경변수로 분기 (local: MinIO, prod: AWS S3)
- 키 컨벤션: [`/docs/storage.md §2`](../docs/storage.md)
- bucket은 환경변수, key만 DB 저장

---

## 11. GitHub API

- `github/infrastructure/GithubApiClient.java`
- WebClient 기반, `Authorization: Bearer {github_access_token}`
- 토큰은 `GithubTokenCipher`로 복호화한 평문을 메모리에서만 사용
- rate-limit 응답(403 + remaining=0) 처리: 429로 변환 + retry-after 응답

---

## 12. Flyway 마이그레이션

- `src/main/resources/db/migration/V{n}__{snake_case}.sql`
- 적용 후 수정 절대 금지 (수정 시 새 V 추가)
- DDL과 DML 분리
- 상세: [`/docs/database.md §8`](../docs/database.md)

---

## 13. 환경 변수

`application.properties` + `application-{profile}.properties` + 환경변수.

```properties
spring.application.name=stackup
spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:stackup}
spring.datasource.username=${POSTGRES_USER:stackup}
spring.datasource.password=${POSTGRES_PASSWORD:stackup}
spring.jpa.hibernate.ddl-auto=validate   # Flyway 사용 → validate
```

전체 변수 목록: [`/docs/environment.md §3`](../docs/environment.md).

---

## 14. 로깅

- Logback JSON 포맷 (운영) / human-readable (로컬)
- MDC에 `traceId`, `userId`
- 민감정보 마스킹: `common/log/PiiMasker.java`
- 자세한 정책: [`/docs/observability.md`](../docs/observability.md)

---

## 15. 테스트

- 단위: `*Test.java` (Spring 컨텍스트 X, 빠름) — 기본값. 대부분 여기서 끝낸다.
- **리포지토리: `*RepositoryTest.java` + `@PostgresRepositoryTest`** — 실제 PG(+pgvector) 컨테이너 (아래 §15.1)
- 통합: `*IT.java` 또는 `*IntegrationTest.java` + Testcontainers (PG/RabbitMQ)
- 아키텍처: `*ArchTest.java` (ArchUnit) — 의존성 방향·패키지 규칙 검증 (§16)
- Builder 패턴으로 fixture (`UserBuilder.aUser()`)
- 자세한 전략: [`/docs/testing-strategy.md`](../docs/testing-strategy.md)

### 15.1 `@PostgresRepositoryTest` — 쿼리 검증

기본 테스트 프로파일(`application-test.yml`)은 DataSource·Hibernate·Flyway 오토컨피그를
**제외**하고 리포지토리를 전부 목으로 대체한다. 빠르지만 그 대가로 **`@Query` 의 JPQL 이
아무 검증도 받지 못한다** — 문법 오류는 물론이고 "삭제된 행을 안 걸렀다" 같은 의미 결함도
그대로 통과한다(실제로 통계 쿼리 5개가 그랬다. `SessionFeedbackRepositoryTest` 참고).

`@PostgresRepositoryTest` 를 붙이면 실제 PostgreSQL 컨테이너에서 돈다:

```java
@PostgresRepositoryTest
class SessionFeedbackRepositoryTest {
    @Autowired SessionFeedbackRepository feedbackRepository;
    // ...
}
```

- 이미지는 `pgvector/pgvector:pg17` — `infra/postgres/Dockerfile` 과 같은 계열이어야 한다
  (마이그레이션이 vector 타입·인덱스를 쓴다). **운영 PG 버전을 올리면 `PostgresTestContainer`
  의 태그도 같이 올린다.**
- 스키마는 운영과 같은 **Flyway 마이그레이션**으로 만들고 `ddl-auto: validate` 를 건다 →
  엔티티 매핑과 마이그레이션이 어긋나면 컨텍스트 로딩에서 바로 터진다. 배포 후에야 알던
  사고를 CI 로 당기는 부수 효과.
- 컨테이너는 **JVM 당 하나**만 뜬다(`PostgresTestContainer`). 테스트가 늘어도 기동 비용은 한 번.
- `@DataJpaTest` 라 각 테스트는 트랜잭션 안에서 돌고 끝나면 롤백된다.
- CI(`ubuntu-latest`)는 Docker 가 이미 있어 별도 설정이 필요 없다. 다만 첫 실행에 이미지를
  받으므로 백엔드 잡이 그만큼 길어진다 — **쿼리 동작을 봐야 하는 테스트에만** 쓰고,
  서비스 로직은 계속 Mockito 단위 테스트로 다룬다.

## 16. ArchUnit — 아키텍처 룰 자동 검증

도메인 우선 패키지 구조 + 레이어 의존성 방향(§3)을 **빌드 단계에서 강제**한다. 사람의 리뷰가 놓치기 쉬운 위반을 컴파일/테스트로 차단.

### 의존성 추가
```gradle
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

### 권장 룰 (`src/test/java/com/stackup/stackup/architecture/ArchitectureTest.java`)

```java
@AnalyzeClasses(packages = "com.stackup.stackup",
                importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // 1. 도메인 패키지 의존 방향 (presentation → application → domain)
    @ArchTest
    static final ArchRule domain_should_not_depend_on_application_or_presentation =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..", "..presentation..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_presentation =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..");

    // 2. 컨트롤러는 서비스만 의존 (Repository 직접 호출 금지)
    @ArchTest
    static final ArchRule controllers_should_not_use_repositories =
        noClasses().that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat().areAssignableTo(JpaRepository.class);

    // 3. Entity setter 노출 금지
    @ArchTest
    static final ArchRule entities_should_not_have_public_setters =
        noMethods().that().areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .should().bePublic().andShould().haveNameStartingWith("set");

    // 4. 도메인 간 순환 의존 금지
    @ArchTest
    static final ArchRule no_cyclic_dependencies_between_domains =
        slices().matching("com.stackup.stackup.(*)..").should().beFreeOfCycles();

    // 5. @Transactional은 application(service) 레이어에서만
    @ArchTest
    static final ArchRule transactional_only_in_application_layer =
        classes().that().areAnnotatedWith(Transactional.class)
            .should().resideInAPackage("..application..");

    // 6. JPA 엔티티는 domain 패키지에만
    @ArchTest
    static final ArchRule entities_should_reside_in_domain_package =
        classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..");

    // 7. Native query 사용 금지 (필요시 specific 케이스만 화이트리스트)
    @ArchTest
    static final ArchRule no_native_query_outside_whitelist =
        noClasses().that().resideOutsideOfPackages(
                "..document.infrastructure..")  // pgvector 검색은 예외
            .should().dependOnClassesThat().haveNameMatching(".*NativeQuery.*");
}
```

### CI 통합
- `./gradlew test` 에 자동 포함됨 (별도 task 분리 불필요)
- 위반 발견 시 빌드 실패 → PR 머지 차단
- 신규 룰 추가 시 기존 코드를 `freeze` (당장 위반은 허용, 신규는 차단)할 수도 있음 (`FreezingArchRule`)

### 룰 진화 원칙
- **합의된 규약만 코드화**. 본 문서/도메인 패키지 가이드에 적힌 것만 ArchUnit에 옮긴다.
- 위반이 정당화될 때는 룰을 풀기보다 **예외 패키지 명시** (위 §7 native query처럼)
- 룰 추가 PR은 본 문서·도메인 가이드 동시 갱신

---

## 16. 빌드·실행

```bash
./gradlew bootRun                    # 개발 실행
./gradlew test                       # 단위·통합 테스트
./gradlew build                      # JAR 생성
./gradlew dependencyCheckAnalyze     # 취약점 스캔 (도입 후)
java -jar build/libs/stackup-0.0.1-SNAPSHOT.jar
```

로컬 의존성(PG/RabbitMQ/MinIO):
```bash
docker compose up -d
```

---

## 17. 코드 스타일

- Lombok 적극 사용 OK (`@Getter`, `@RequiredArgsConstructor`, `@Builder`). 단 entity는 `@Setter` 금지.
- record는 DTO/응답에 우선 사용
- final 필드 + 생성자 주입
- 상수는 도메인 클래스 내부 `private static final`
- 자세한 공통 규약: [`/docs/coding-conventions.md`](../docs/coding-conventions.md)

---

## 18. API 노출

- springdoc-openapi (도입 시):
  - `/api/v3/api-docs` — OpenAPI JSON
  - `/api/swagger-ui.html` — UI
- 신규 endpoint는 `@Operation`, `@ApiResponse` 작성
- API 규약: [`/docs/api-conventions.md`](../docs/api-conventions.md)

---

## 19. 현재 상태 (2026-05 기준)

- 도메인 본 구현 진행 — auth · user(consent 포함) · github · resume · document · session · log.ai
- Spring Security + JWT + GitHub OAuth (US-01) 본 구현
- **Google 로그인 본 구현**: `POST /api/auth/google` + `GET /api/auth/google/callback` (PKCE·state 검증은
  GitHub 과 동일 경로 재사용). V22 로 `users` 에 `provider`/`google_id`/`display_name` 추가하고
  `github_id`·`github_username`·`encrypted_github_access_token` 을 nullable 로 전환 + provider 별 식별자
  CHECK 제약. `OAuthProvider` 는 `user.domain` 에 둔다 — `auth.domain` 에 두면 User 가 참조하며
  user→auth→user 순환이 생겨 ArchUnit 이 막는다. Google 은 **토큰을 저장하지 않는다**(신원만 필요).
  GitHub 토큰이 필요한 기능은 `InternalGithubTokenService` 에서 `AUTH_GITHUB_NOT_LINKED`(409) 로 차단.
  `app.google.client-id/secret` 에는 `@NotBlank` 를 걸지 않았다 — 자동 배포가 시크릿 없이 돌면
  애플리케이션 전체가 부팅 실패하기 때문. 미설정 시 Google 로그인만 비활성.
- RabbitMQ starter 본 구현 — Core ↔ AI envelope · DLX · DLQ · 멱등(`processed_messages`) 완비
- Flyway 본 구현 — 모든 테이블 / pgvector index / ENUM CHECK
- ArchUnit 룰 적용 (의존 방향 · 순환 차단 · `@Transactional` application 한정 · entity는 domain 패키지)
- 면접 도메인 (US-13~20) 본 구현: 세션 CRUD/start/end/interrupt, generate.questions 발행,
  callback.questions(POOL/FOLLOWUP) 수신, 자동 종료
- **세션 시간초과 자동 종료 본 구현**: `@EnableScheduling`(`common/config/SchedulingConfig`) + `SessionTimeoutSweeper`
  (`@Scheduled` 기본 5분 주기)가 `maxDurationMinutes` 초과한 IN_PROGRESS 세션을 찾아 `SessionTimeoutService.endTimedOut`
  호출 — 답변 있으면 COMPLETED(→`SessionEndedEvent(DURATION_EXCEEDED)`→피드백), 없으면 INTERRUPTED(피드백 없음).
  좀비 세션(자기소개 미답변·STT 실패·탭 종료) 방지.
  주기: `interview.session.sweep-interval-ms`(기본 300000)·`sweep-initial-delay-ms`(기본 60000).
  - **동시 종료 안전(원자적 전이)**: 모든 종료 경로(스위퍼·수동 `SessionService.end`·콜백 `endSession`)는
    `InterviewSessionRepository.finishIfInProgress`(조건부 UPDATE `WHERE status=IN_PROGRESS`)로 전이를
    차지하고, **영향 행 1인 트랜잭션만** `SessionEndedEvent` 를 발행한다 → DB 행 락으로 직렬화돼 동시
    종료 시에도 `generate.feedback` 가 중복 발행되지 않는다.
  - **모든 상태 전이가 조건부 UPDATE**: `start`(`startIfReady`)·`cancel`(`cancelIfReady`)·`interrupt`
    (`finishIfInProgress(INTERRUPTED)`)도 같은 패턴 — 엔티티 검증만으로는 두 트랜잭션이 같은 스냅숏을
    읽고 둘 다 통과할 수 있다. 영향 행 0 이면 `SESSION_INVALID_STATE`.
  - **종료 후 콜백 드롭**: `QuestionsCallbackService.apply` 와 `SessionFollowupRequester.onAnswerSubmitted` 는
    세션이 `isTerminal()` 이면 처리를 건너뛴다 → 자동종료 뒤 늦게 도착한 POOL/FOLLOWUP 콜백이나
    막판 답변 발화가 종료 세션에 질문·placeholder 를 추가하는 사후 변조를 차단.
- **피드백 공유 해제·재생성 본 구현**: `DELETE /api/sessions/{id}/feedback/share`(토큰 소거 — 기존 링크
  즉시 404, 멱등), `POST /api/sessions/{id}/feedback/regenerate`(202 — COMPLETED 인데 피드백이 없을 때만
  `generate.feedback` 재발행; 있으면 409 `FEEDBACK_ALREADY_EXISTS`). 공개 조회(`getByToken`)는 세션이
  soft delete 되면 토큰이 남아 있어도 404 — '기록 삭제'가 공유 링크에도 미치게. `shareToken` 은 소유자
  응답(`FeedbackResponse.from`)에만 노출, 공개 응답(`fromPublic`)에선 제거.
- **피드백 하이라이트 본 구현**: V21 로 `session_feedbacks.highlights`(JSONB) 추가. AI 가 강점/개선점 본문에서
  핵심 구절 3~6개를 **그대로 발췌**(부분 문자열 매칭 보장)해 `callback.feedback.highlights[]` 로 보내고,
  `FeedbackResponse` 가 소유자·공유 엔드포인트 모두에 노출. 프론트는 이 구절 ∪ 다음에 채울 키워드를
  리포트 문단에서 `<mark>` 강조(`HighlightedText`).
- **질문별 복기 본 구현**: V19 로 `interview_messages` 에 `model_answer`/`answer_rewrite`/`coaching_comment`
  추가. AI 가 `callback.feedback.answerCoaching[{messageId,…}]` 로 답변별 모범 답안·리라이트·코칭을 보내면
  `FeedbackCallbackService` 가 각 메시지에 `recordCoaching` 기록. `MessageResult`/`MessageResponse` 가 답변
  평가 점수 + 복기 + **답변 전달력 메트릭(WPM·무음·간투어, `MessageVoiceAnalysis` 에서 파싱)** 을 노출하되
  **종료 세션 조회에서만**(`expectedSignal` 과 동일 게이팅). 프론트는 답변 버블 아래 '복기' 아코디언으로 표시.
- **전달력 피드백 강화 본 구현**: `MessageResult`/`MessageResponse` 가 `pronunciationAccuracy`(STT 신뢰도 근사)
  와 **결정론적 전달력 평가**(`DeliveryFeedback.assess` — 어절/분·무음 비율·100어절당 간투어·발음 임계치 →
  `deliveryRating` GOOD/FAIR/POOR + `deliveryComment` 한 줄 코칭, LLM 비호출)를 추가 노출(종료 세션만, 음성 답변만).
  자기소개 첫인상은 세션 평균이 아니라 **자기소개 답변 단독 음성 지표**로 평가 — `SessionFeedbackRequester` 가
  `generate.feedback.selfIntroVoiceAnalysis`(자기소개 답변의 WPM/무음/간투어)를 동봉하고 AI 첫인상 평가가 이를 사용.
- **직무 맞춤 면접 모드(JOB_TAILORED) 본 구현**: `SessionMode.JOB_TAILORED` 추가(V18 — mode CHECK 갱신 +
  `target_company_name`/`target_job_description` 컬럼). 이 모드는 회사명+채용공고(JD)를 받아(JD 필수,
  `SessionService` 검증 `SESSION_JD_REQUIRED`) `InterviewSession.assignTargetRole` 로 보관. JD 는
  `SelfIntroAnsweredEvent`→`generate.questions`(적합도·지원동기 질문)와 `generate.feedback`(직무 적합도
  평가)에 함께 실린다. 다른 모드는 JD 무시(null).
- **첫 질문 자기소개 고정 본 구현**: 모든 면접의 첫 질문은 `InterviewMessage.selfIntroduction`(seq=1,
  category=`SELF_INTRODUCTION`)으로 세션 생성 직후 AI 없이 삽입(`QuestionsCallbackService.insertSelfIntroduction`,
  `SessionQuestionsRequester.onSessionCreated`). 질문 풀은 자기소개 **답변**을 받은 뒤 발행한다 —
  `SessionFollowupRequester` 가 `parent.isSelfIntroduction()` 면 꼬리질문 대신 `SelfIntroAnsweredEvent` 발행 →
  `SessionQuestionsRequester.onSelfIntroAnswered` 가 답변(`selfIntroAnswer`)을 씨앗으로 `generate.questions`
  발행(풀 크기 = `generalQuestionCount-1`, 자기소개 1자리 예약). 자기소개엔 꼬리질문을 달지 않는다.
- 질문 TTS 발행 본 구현: 질문 영속 후 `QuestionPersistedEvent`(AFTER_COMMIT) → `SessionTtsRequester` 가 `generate.tts` 발행,
  `callback.tts` 수신해 메시지에 오디오 경로 반영(`TtsCallbackService`)
- **꼬리질문 토큰 스트리밍 placeholder 본 구현**: `SessionFollowupRequester` 가 AI 호출 분기에서 INTERVIEWER placeholder(`InterviewMessage.followupPlaceholder`, content=`"(생성 중)"`, status=`CREATED`)를 선INSERT + `SESSION_MESSAGE` 발행 + `generate.followup.followupMessageId` 동봉. AI 가 토큰을 `SESSION_MESSAGE_DELTA` 로 흘린 뒤 `callback.questions(FOLLOWUP)` 도착 시 `QuestionsCallbackService` 가 분기: NORMAL→`completeFollowup` UPDATE+카운트, CLARIFICATION→UPDATE(카운트 X), DONT_KNOW→placeholder DELETE 후 `advanceToNextGeneral`. placeholder 없는 레거시 콜백은 기존 INSERT 폴백.
- **질문 풀/꼬리질문 생성 실패 신호 본 구현**: AI 가 `generate()` 실패를 조용히 DLQ 로만 흘려서 세션이
  "생성 중"에 무기한 멈추던 문제를 고쳤다. `QuestionsCallbackPayload`에 `status`(`OK`|`FAILED`)·
  `errorCode`·`errorMessage`·`retriable` 필드 추가(구버전 9-arg 생성자는 `status=OK` 로 위임하는
  오버로드로 하위호환). `QuestionsCallbackService.apply` 가 kind 분기 전에 `isFailed()` 를 먼저 확인:
  POOL 실패는 저장할 게 없고 재시도를 트리거하는 곳도 없어 세션을 바로 정상 종료시켜 피드백 흐름을
  태운다(`endSessionOnPoolFailure` — "질문 준비 중" 무기한 대기 방지). FOLLOWUP 실패는 placeholder 를 삭제하지 않고
  `InterviewMessage.failFollowup()`(content=`FOLLOWUP_GENERATION_FAILED_TEXT`, status=`FAILED`)로
  확정한 뒤 `SESSION_MESSAGE`(`FOLLOWUP_FAILED`) 발행 + DONT_KNOW 와 동일하게 `advanceToNextGeneral`
  로 다음 일반질문으로 진행 — 턴이 사라진 것처럼 보이지 않으면서 면접은 멈추지 않는다.
- **피드백 생성 실패 신호 본 구현**: AI `feedback_consumer` 의 예상 못 한 예외가 DLQ 로만 격리돼
  세션이 "피드백 생성 중"에 무기한 멈추던 gap 을 닫았다. `FeedbackCallbackPayload` 에 `status`
  (`OK`|`FAILED`)·`errorCode`·`errorMessage`·`retriable` 추가(구버전 13-arg 생성자는 `status=OK`
  위임 오버로드로 하위호환). `FeedbackCallbackService.apply` 가 저장 전에 `isFailed()` 를 확인:
  실패면 저장 없이 `SseEventType.ERROR`(`SessionErrorNotice`, scope=`FEEDBACK`,
  code=`FEEDBACK_GENERATION_FAILED`)를 세션/유저 채널에 발행하고 멱등 마킹만 한다. AI 의
  `errorMessage` 원문은 서버 로그에만 남기고 클라이언트에는 화이트리스트 문구만 보낸다
  (QuestionsCallbackService 와 동일 원칙). AI 쪽 발행은 [`ai/CLAUDE.md`](../ai/CLAUDE.md) 참고.
- **문장 단위 TTS 세그먼트 프록시 본 구현 (Part B)**: `InterviewMessageService.streamAudioSegment` + `GET /api/sessions/{sid}/messages/{mid}/audio/segments/{seq}?ext=`. AI 가 휘발성으로 쓴 라이브 세그먼트를 규칙(`interview/tts/{sid}/{mid}/seg-{seq}.{ext}`)으로 재구성해 프록시(DB 미기록). 소유권+ext 화이트리스트+seq>=0 검증으로 임의 키 노출 차단.
- AI 호출 로깅 (US-30) 본 구현: `/api/internal/ai-logs` + `ai_request_logs` INSERT
- **웹 이력서(URL) 본 구현 (US-09)**: `POST /api/resumes/web { url }`. AI 서버에 웹 분석이 이미
  완성돼 있었는데(`analyze.web` consumer) Core 발행부가 없어 반쪽이던 걸 배선했다. `docs/messaging.md §5.3`
  대로 **resume 도메인을 재사용** — V24 로 `resumes` 에 `file_type='WEB'`·`source_url` 추가(+`file_path`
  nullable, 타입별 필수 locator CHECK). 흐름은 PDF 와 대칭:
  `ResumeService.registerWeb` → `WebResumeRegisteredEvent` → `WebResumeAnalysisEventListener` →
  `AnalysisRequestService.requestWebResumeAnalysis`(AnalyzedDocument 생성) → AFTER_COMMIT `analyze.web` 발행.
  콜백은 `AnalysisCallbackService` 가 `context.documentId` 로 처리하므로 **무변경 재사용**.
  - **SSRF 가드 필수** (`WebResumeUrlValidator`): 사용자 URL 을 AI 서버가 그대로 fetch 하고, AI 는 docker
    네트워크에서 Core·PG·RabbitMQ·MinIO 에 닿는다. http(s) 만 허용 + userinfo 거부 + 호스트를 **해석한
    주소**로 사설/루프백/링크로컬/멀티캐스트/IPv6 unique-local 차단(이름이 아니라 주소로 판단하므로 사설
    IP 로 해석되는 공개 도메인도 막힌다). 거부 응답에 내부 주소는 노출하지 않는다. DNS 해석기는 주입
    가능(테스트가 네트워크 미의존). 여긴 첫 관문이고 실질 방어선은 AI 쪽 `url_guard.py` 다.
  - 같은 URL 재등록은 409(`RESUME_URL_DUPLICATE`) — 임베딩 중복으로 질문이 쏠리는 것 방지.
- **같은 설정으로 다시 면접 본 구현**: `POST /api/sessions/{id}/retry` → `SessionService.retry` 가
  원본 세션의 설정(모드·직군·질문 수·JD 등)을 복사해 `create` 를 호출한다. **연결 자료는 지금도
  살아있고 ANALYZED 인 것만** 다시 잇는다 — `linkContexts` 는 삭제된 문서에 `DOC_NOT_FOUND`(404),
  분석 미완료에 `DOC_NOT_ANALYZED` 를 던지므로, 원본 설정을 그대로 재전송하면 그 사이 자료 하나
  지운 사용자는 재도전 자체가 막힌다. 빠진 자료는 응답 `contextDocumentIds` 를 원본과 비교해
  프론트가 안내한다.
- **약점 집중 재도전 본 구현 (B-3)**: `POST /api/sessions/{id}/retry` 에 `{"focusOnWeakness":true}`
  를 주면 `SessionService.retry` 가 원본 세션 피드백에서 낮은 평가 축을 골라 새 세션의
  `focus_areas`(V25, JSONB, `SessionFocusArea` name 배열)에 새긴다. 선정 기준: 임계값
  (`interview.weakness-focus.score-threshold`, 기본 70) 미만인 축을 낮은 순 최대 2개.
  **전부 기준 이상이어도 가장 낮은 하나는 고른다** — 눌렀는데 아무것도 안 바뀌면 고장으로 보인다.
  피드백이 없으면(중단 세션) 빈 목록이라 일반 재도전과 같아진다.
  `SessionQuestionsRequester` 가 세션에서 읽어 `generate.questions.focusAreas` 로 싣고,
  AI 프롬프트가 그 영역을 검증하는 질문을 과반으로 배치한다(단, 자료 근거 없는 질문 생성 금지 —
  target_evidence 필수 조건은 면제되지 않는다).
  - `@Value` 필드에 **자바 초기값도 함께** 둔다(`= 70`). Spring 밖(단위 테스트)에서는 주입이
    안 돼 0.0 이 되고, 그러면 모든 축이 '기준 이상'으로 판정돼 조용히 다른 동작을 한다.
- **오답노트 본 구현 (B-4)**: `PUT /api/sessions/{sid}/messages/{mid}/bookmark` (표시/해제) +
  `GET /api/users/me/bookmarks` (모아보기). V26 으로 `interview_messages.bookmarked` + 부분 인덱스.
  **질문(INTERVIEWER) 메시지에만** 걸 수 있다(`MESSAGE_NOT_BOOKMARKABLE`) — 답변을 표시해도 복습할 게 없다.
  요청은 토글이 아니라 **명시적 상태**를 받는다: 토글이면 재전송·더블클릭이 상태를 뒤집는다.
  목록은 질문 + 그때 내 답변 + 모범답안/코칭을 한 묶음으로 반환하며, 답변은
  `findByParentMessage_IdIn` 으로 한 번에 받아 매핑한다(질문마다 조회하면 N+1).
  `QuestionBookmarkController` 는 URL 이 `/api/users/me/*` 지만 `UserStatsController` 와 같은 이유로
  session 슬라이스에 둔다(user → session 직접 의존 회피).
- **중단 세션 이어하기 본 구현 (B-5)**: `PATCH /api/sessions/{id}/resume` — INTERRUPTED 만 재개
  가능(완료·취소는 422, 새로 하려면 `/retry`). `resumeIfInterrupted` 조건부 UPDATE 로 전이를
  차지하고 `ended_at` 을 지우며 `resumed_at`(V27)을 찍는다.
  - **시간 한도 기준을 `durationAnchor()`(= resumedAt ?? startedAt) 로 바꿨다.** startedAt 기준
    그대로면 한참 뒤 재개했을 때 스위퍼가 즉시 다시 중단시킨다. startedAt 은 '처음 시작한 시각'
    으로 보존된다. 이어하기를 반복하면 총 시간이 늘어나지만, 연습 도구라 허용하는 트레이드오프.
  - **조건부 UPDATE 뒤에는 인메모리 상태를 반드시 맞춘다**(`session.resume(now)`). 벌크
    UPDATE 는 영속성 컨텍스트를 갱신하지 않아서, 같은 트랜잭션에서 `findById` 해도 1차 캐시의
    낡은 엔티티(INTERRUPTED)가 돌아온다. 그러면 `advanceToNextGeneral` 이 상태 검사에서
    조용히 되돌아가 복구가 통째로 죽고 응답 status 도 INTERRUPTED 로 나간다.
    `SessionService.start` 가 `startIfReady` 뒤에 `session.start()` 를 부르는 것과 같은 이유.
  - **핵심은 전이가 아니라 끊긴 턴 복구다**(`SessionResumeService.recoverTurn`). 중단은 보통 턴
    한가운데서 일어나고 그동안 온 콜백은 terminal 가드가 전부 드롭했다. 마지막 메시지로 분기:
    정상 질문이면 그대로(답하면 됨) / "(생성 중)" placeholder 면 `failFollowup` + 다음 일반질문 /
    자기소개 답변인데 풀이 0건이면 `SelfIntroAnsweredEvent` 재발행(넘기면 POOL_EXHAUSTED 로
    세션이 끝나버린다) / 그 외 답변이면 다음 일반질문.
  - **재개는 terminal 가드의 전제를 깬다.** 종료 세션에 늦게 온 콜백은 `isTerminal()` 로 드롭되지만,
    재개된 세션은 IN_PROGRESS 라 그대로 통과한다. 그래서 `applyFollowup` 이 **이미 FAILED 인
    placeholder 를 되살리지 않도록** 막는다 — 복구가 실패 확정 + 다음 질문까지 마친 뒤 늦은 콜백이
    그 자리를 채우면 살아있는 질문이 두 개가 된다. POOL 은 `countBySessionId > 0` 로 이미 멱등.
- **STT 콜백 유실 복구 본 구현**: 음성 답변은 `(transcribing)` placeholder 로 먼저 저장되고
  `callback.voice` 가 도착해야 채워진다. 그 콜백이 유실되면(AI 크래시·DLQ 격리·브로커 단절)
  메시지가 그 상태로 남고 프론트 턴 판정상 **답변 차례가 오지 않아 면접이 멈춘다** —
  세션 시간 초과로 통째로 끝날 때까지. 질문 생성 쪽은 실패 신호로 이미 해결했지만 음성엔
  대응이 없었다. `StaleTranscriptionSweeper`(기본 2분 주기)가 `interview.voice.
  stale-transcription-minutes`(기본 5분)를 넘긴 placeholder 를 찾아
  `VoiceTranscriptionRecoveryService.failStaleTranscription` 으로 FAILED 확정한다
  (`STT_CALLBACK_TIMEOUT`). 그러면 기존 STT 실패 경로를 그대로 타서 사용자가 같은 질문에
  텍스트로 다시 답할 수 있다. 목록 생성 후 콜백이 도착한 경우를 위해 확정 직전 상태를 다시
  확인한다(완료된 답변을 실패로 되돌리지 않는다).
- **Spring AI 미사용** — LLM·임베딩 호출은 모두 AI 서버 위임. Core는 RabbitMQ 발행만 담당.
- **Redis 미사용** — 휘발성 데이터는 DB short-lived 레코드 또는 인메모리로.

각 도입 시 본 문서 §1, 관련 도메인 `CLAUDE.md` 갱신.
