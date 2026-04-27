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
| 추가 예정 | Spring Security, Spring AI, RabbitMQ starter, Redis, Flyway, springdoc-openapi |

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
| `document` | 분석 문서(이력서/레포 공통) 메타 + S3 경로 | US-09~12 |
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
    @NotNull InterviewType interviewType,
    @NotNull JobCategory jobCategory,
    @Min(1) @Max(30) Integer maxQuestions,
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
- Builder 패턴으로 fixture (`UserBuilder.aUser()`)
- 자세한 전략: [`/docs/testing-strategy.md`](../docs/testing-strategy.md)

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

## 19. 현재 상태 (2026-04 기준)

- 도메인 패키지 골격만 존재, 실제 구현 거의 없음
- Spring Security 미도입 → US-01 작업 시 도입
- RabbitMQ starter 미도입 → US-09 작업 시 도입
- Flyway 미도입 → 첫 entity 작성 PR에서 도입
- Spring AI 추가 검토 (벡터 검색을 Core가 제공한다면)

각 도입 시 본 문서 §1, 관련 도메인 `CLAUDE.md` 갱신.
