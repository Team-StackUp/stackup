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
| `auth` | GitHub OAuth flow, JWT 발급/갱신/검증, refresh token, JWT 필터 | US-01 |
| `user` | 사용자 CRUD, 회원 탈퇴, 프로필 조회 | US-02, US-04 |
| `user.consent` | 개인정보처리동의 기록·조회·철회 | US-03 |
| `github` | GitHub API 연동, 레포 목록/등록/메타 동기화 | US-07, US-08 |
| `resume` | 이력서 업로드(S3)·메타 저장·목록·삭제 | US-05, US-06 |
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

- 단위: `*Test.java` (Spring 컨텍스트 X, 빠름)
- 통합: `*IT.java` 또는 `*IntegrationTest.java` + Testcontainers (PG/RabbitMQ)
- 아키텍처: `*ArchTest.java` (ArchUnit) — 의존성 방향·패키지 규칙 검증 (§16)
- Builder 패턴으로 fixture (`UserBuilder.aUser()`)
- 자세한 전략: [`/docs/testing-strategy.md`](../docs/testing-strategy.md)

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
  - **종료 후 콜백 드롭**: `QuestionsCallbackService.apply` 와 `SessionFollowupRequester.onAnswerSubmitted` 는
    세션이 `isTerminal()` 이면 처리를 건너뛴다 → 자동종료 뒤 늦게 도착한 POOL/FOLLOWUP 콜백이나
    막판 답변 발화가 종료 세션에 질문·placeholder 를 추가하는 사후 변조를 차단.
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
  POOL 실패는 저장할 게 없어 `SseEventType.ERROR`(`SessionErrorNotice`)로 세션/유저 채널에만 알리고
  세션 상태는 그대로 둔다(재시도 트리거는 후속 과제). FOLLOWUP 실패는 placeholder 를 삭제하지 않고
  `InterviewMessage.failFollowup()`(content=`FOLLOWUP_GENERATION_FAILED_TEXT`, status=`FAILED`)로
  확정한 뒤 `SESSION_MESSAGE`(`FOLLOWUP_FAILED`) 발행 + DONT_KNOW 와 동일하게 `advanceToNextGeneral`
  로 다음 일반질문으로 진행 — 턴이 사라진 것처럼 보이지 않으면서 면접은 멈추지 않는다.
- **문장 단위 TTS 세그먼트 프록시 본 구현 (Part B)**: `InterviewMessageService.streamAudioSegment` + `GET /api/sessions/{sid}/messages/{mid}/audio/segments/{seq}?ext=`. AI 가 휘발성으로 쓴 라이브 세그먼트를 규칙(`interview/tts/{sid}/{mid}/seg-{seq}.{ext}`)으로 재구성해 프록시(DB 미기록). 소유권+ext 화이트리스트+seq>=0 검증으로 임의 키 노출 차단.
- AI 호출 로깅 (US-30) 본 구현: `/api/internal/ai-logs` + `ai_request_logs` INSERT
- **Spring AI 미사용** — LLM·임베딩 호출은 모두 AI 서버 위임. Core는 RabbitMQ 발행만 담당.
- **Redis 미사용** — 휘발성 데이터는 DB short-lived 레코드 또는 인메모리로.

각 도입 시 본 문서 §1, 관련 도메인 `CLAUDE.md` 갱신.
