# `com.stackup.stackup` — 도메인 패키지 가이드

> 모든 도메인 패키지가 따라야 할 공통 규칙. 새 도메인 추가 시 본 문서를 기준으로 구조를 잡는다.

상위: [`/backend/CLAUDE.md`](../../../../../CLAUDE.md)

---

## 1. 도메인 패키지 표준 구조

```
{domain}/
├── domain/                  # 핵심: Entity, Enum, VO, Repository(interface)
│   ├── {Aggregate}.java
│   ├── {AggregateRepository}.java     # interface (Spring Data JPA)
│   ├── {AggregateRepositoryCustom}.java + Impl  # QueryDSL 커스텀
│   ├── {EnumOrVO}.java
│   └── exception/
│       └── {Aggregate}Exception.java
├── application/             # 비즈니스 로직, 트랜잭션 경계
│   ├── {Aggregate}Service.java
│   ├── {Aggregate}Facade.java        # 여러 도메인 조립 시
│   ├── dto/
│   │   ├── {Aggregate}CreateCommand.java
│   │   └── {Aggregate}Result.java
│   └── event/
│       └── {AggregateCreated}Event.java
├── presentation/            # HTTP I/O
│   ├── {Aggregate}Controller.java
│   └── dto/
│       ├── {Aggregate}CreateRequest.java
│       └── {Aggregate}Response.java
└── infrastructure/          # 외부 연동
    ├── {Aggregate}EventPublisher.java
    ├── {Aggregate}MessageHandler.java
    └── {External}Client.java
```

> 단순 도메인은 `application/dto`, `event/`, `infrastructure/` 가 비어 있을 수 있다 — 만들 때까지 패키지 미생성 OK.

---

## 2. 의존성 방향

```
presentation  →  application  →  domain
                     ↓
              infrastructure
                     ↓
              (외부 — DB, RabbitMQ, S3, GitHub)
```

- presentation은 application까지만 의존
- application은 domain만 의존 (인터페이스 통해 infrastructure 사용)
- domain은 다른 도메인의 entity 직접 참조 금지 — `id`만 보유, lookup은 service에서

---

## 3. Entity 규칙

### 3.1 BaseEntity 상속
```java
// common/entity/BaseEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

`is_deleted`가 있는 도메인은 `SoftDeletableEntity` 별도 추상 클래스 사용 검토.

### 3.2 Setter 금지
- `@Setter` 클래스 레벨 금지
- 변경은 의미 있는 도메인 메서드로:
  ```java
  public void start() {
      if (status != READY) throw new SessionNotReadyException(id);
      this.status = IN_PROGRESS;
      this.startedAt = Instant.now();
  }
  ```

### 3.3 Enum 매핑
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private SessionStatus status;
```

ORDINAL 절대 금지. DDL의 CHECK 제약 + Enum 1:1.

### 3.4 ID
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

PG는 IDENTITY 사용 (BIGSERIAL이 IDENTITY와 호환).

### 3.5 연관관계
- `@ManyToOne` 기본 LAZY (`fetch = FetchType.LAZY`)
- `@OneToMany` cascade 신중 — 의도 없는 삭제 방지
- 양방향 매핑은 정말 필요할 때만 (보통 단방향으로 충분)

---

## 4. Repository 규칙

### 4.1 인터페이스만 노출
```java
public interface ResumeRepository extends JpaRepository<Resume, Long>, ResumeRepositoryCustom {
    Optional<Resume> findByIdAndIsDeletedFalse(Long id);
    List<Resume> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);
}
```

### 4.2 QueryDSL 커스텀
```java
public interface ResumeRepositoryCustom {
    Page<Resume> search(ResumeSearchCondition cond, Pageable pageable);
}

@RequiredArgsConstructor
public class ResumeRepositoryImpl implements ResumeRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    @Override
    public Page<Resume> search(...) {
        // queryFactory.selectFrom(QResume.resume)...
    }
}
```

### 4.3 Native Query
- `pgvector` 검색 등 ORM이 표현 어려운 경우만
- 항상 named parameter (`:userId`), 문자열 concat 금지

---

## 5. Service 규칙

### 5.1 트랜잭션
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)              // 클래스 레벨 readOnly
public class ResumeService {
    private final ResumeRepository repo;

    @Transactional                            // 쓰기 메서드만 명시
    public Resume upload(Long userId, ResumeUploadCommand cmd) { ... }
}
```

### 5.2 외부 호출은 트랜잭션 밖
```java
public Resume upload(...) {
    String s3Key = storage.put(file);          // 트랜잭션 시작 전
    return txTemplate.execute(s -> repo.save(...));
}
```
또는 트랜잭션 분리 + outbox 패턴.

### 5.3 권한 체크
- service 메서드 진입 시 owner 검증
- `@PreAuthorize` 또는 명시적 코드

---

## 6. Controller 규칙

```java
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeResponse upload(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestPart ResumeUploadRequest meta,
        @RequestPart MultipartFile file
    ) {
        return ResumeResponse.from(service.upload(principal.userId(), meta.toCommand(file)));
    }
}
```

원칙:
- DTO ↔ Command/Result 변환은 controller에서
- 인증 정보는 `@AuthenticationPrincipal`로 주입
- HTTP 상태 코드 명시 (`@ResponseStatus`)

---

## 7. 이벤트

도메인 이벤트 발행 → application layer에서 publish:
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(ResumeUploadedEvent event) {
    rabbitPublisher.publish("ai.request.resume.analyze", event.toMessage());
}
```

`AFTER_COMMIT` 으로 DB commit 이후 발행 (메시지는 발행됐는데 DB는 롤백되는 케이스 방지).

---

## 8. 테스트

| 종류 | 위치 | 도구 |
|------|------|------|
| 도메인 단위 | `*Test.java` | JUnit + AssertJ (Spring 컨텍스트 X) |
| Repository | `*RepositoryTest.java` | `@DataJpaTest` + Testcontainer PG |
| Service | `*ServiceTest.java` | Mockito 단위 OR Testcontainer 통합 |
| Controller | `*ControllerTest.java` | `@WebMvcTest` 또는 MockMvc + 통합 |
| End-to-end | `*IT.java` | `@SpringBootTest` + Testcontainer 다중 |

---

## 9. 새 도메인 추가 절차

1. 패키지 생성: `com.stackup.stackup.{name}/`
2. `domain/{Aggregate}.java` + Repository
3. Flyway 마이그레이션 작성 (`db/migration/V{n}__add_{name}.sql`)
4. `application/{Aggregate}Service.java`
5. `presentation/{Aggregate}Controller.java`
6. 본 패키지에 `CLAUDE.md` 작성 (선택, 복잡한 도메인은 권장)
7. `/backend/CLAUDE.md §4` 인벤토리 갱신

---

## 10. 안티패턴

- ❌ Entity setter 노출
- ❌ Controller에서 `@Transactional`
- ❌ Service가 다른 service의 Repository 직접 호출 (다른 service의 public 메서드만 사용)
- ❌ DTO를 도메인 패키지에 두기
- ❌ 한 controller가 여러 도메인 처리 (각 도메인의 controller로 분리)
- ❌ 패키지 이름에 layered (`xxx.controller`, `xxx.service`) — 도메인 우선
