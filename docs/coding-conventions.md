# 공통 코딩 컨벤션

> 언어별 세부 규약은 각 레이어 `CLAUDE.md`에 위임. 본 문서는 **모든 언어 공통** 규약.

---

## 1. 명명

### 1.1 식별자
- **변수/함수**: 의미 있는 영문 단어, 약어 자제
- **불리언**: `is`, `has`, `can`, `should` 접두 (`isCompleted`, `hasResume`)
- **컬렉션**: 복수형 (`sessions`, `documentIds`)
- **상수**: 언어별 컨벤션 (Java SCREAMING_SNAKE, JS UPPER_SNAKE, Python UPPER_SNAKE)

### 1.2 도메인 용어
- 코드에서는 **영어**로 통일 (`session`, `interview`, `repository`)
- DB 컬럼은 snake_case
- 한국어 도메인 용어는 [glossary.md](./glossary.md) 매핑 참조

### 1.3 약어 금지 (예외 적은 화이트리스트)
허용: `id`, `url`, `uri`, `api`, `db`, `pg`, `mq`, `ai`, `llm`, `rag`, `stt`, `tts`, `pdf`
비권장: `usr`, `msg`, `cfg`, `tmp` → `user`, `message`, `config`, `temp` 사용

### 1.4 타입 (TypeScript)

프론트엔드 TypeScript 타입 규약은 분리 문서로 관리한다 → [**`frontend-types.md`**](./frontend-types.md).

요점만:
- PascalCase + 의미 있는 접미사 (`Dto` / `Request` / `Response` / `Result` / `Model` / `Props` / `State` / `Options` / `Id`).
- `I` / `T` Hungarian prefix 금지.
- 타입은 사용되는 레이어에 산다. `Dto → Entity → Model → Props` 단방향.
- 전역 `types/` 폴더 금지. 슬라이스 내부에서 시작, 3곳 이상 중복 시 `shared` 승격.

---

## 2. 함수·메서드 설계

- **단일 책임**: 한 함수는 한 가지 일만
- **순수 함수 우선**: 입력 → 출력, 사이드이펙트 격리
- **인자 ≤ 4개**: 그 이상은 객체로 묶기
- **boolean 인자 지양**: 호출부에서 의미 불명. enum 또는 함수 분리.
  ```java
  // 나쁨
  createSession(true, false);

  // 좋음
  createSession(SessionMode.ONLINE, JobCategory.BACKEND);
  ```

---

## 3. 주석

기본은 **주석 없음**. 다음 경우만 주석 작성:

| 케이스 | 예시 |
|--------|------|
| 비명백한 제약 | `// pgvector ivfflat은 list 수 = sqrt(rows) 권장` |
| 외부 워크어라운드 | `// GitHub API 5xx 시 30초 backoff (rate-limit과 구분)` |
| 의도적 트레이드오프 | `// JPA cascade 사용 안 함: 명시적 삭제 흐름 유지` |
| TODO + 컨텍스트 | `// TODO(US-21): STT 청크 단위 처리` |

**금지**:
- 코드 그대로 풀어쓴 주석 (`// loop over users`)
- 변경 이력·작성자 (`// 2026-04-27 수정 - 박상우`) → git blame 사용
- 죽은 코드 보존 (`// 임시로 주석처리`) → 삭제

---

## 4. 에러 처리

### 4.1 예외 vs 에러 코드
- 비즈니스 규칙 위반: 도메인 예외 (`SessionNotInProgressException`)
- 시스템/외부 장애: 일반 예외 (`RuntimeException`, `IOException`)
- API 응답: 도메인 예외 → API error code 매핑 ([api-conventions.md §5](./api-conventions.md))

### 4.2 절대 금지
- 빈 catch 블록 (`} catch (Exception e) {}`)
- 일반 `Exception`을 무차별 catch
- 에러 메시지에 비밀 정보 포함

### 4.3 외부 호출
- timeout 명시 (default 무한대 금지)
- retry는 idempotent 작업만, exponential backoff
- circuit breaker는 의존성 down이 전파되는 호출에 적용

---

## 5. 테스트

### 5.1 무엇을 테스트하나
| Yes | No |
|-----|-----|
| 비즈니스 규칙 (상태 전이, 권한) | trivial getter/setter |
| 경계값 (0, max+1, null) | 프레임워크 기능 (Spring DI 동작 등) |
| 외부 의존성 wrap (RabbitMQ publisher) | 외부 의존성 자체 (RabbitMQ 동작) |
| 회귀 케이스 (버그 fix 후) | |

### 5.2 테스트 명명
```
{메서드명_혹은_시나리오}_{조건}_{기대결과}

예:
createSession_whenUserNotConsented_throwsConsentRequiredException
analyzeResume_whenS3UploadFails_marksResumeFailed
```

### 5.3 Arrange-Act-Assert
```java
// given
var user = aUser().build();
var request = aSessionCreateRequest().mode(ONLINE).build();

// when
var session = sessionService.create(user, request);

// then
assertThat(session.status()).isEqualTo(READY);
```

상세 전략은 [testing-strategy.md](./testing-strategy.md) 참조.

---

## 6. 의존성 관리

### 6.1 새 라이브러리 추가 기준
- 표준 라이브러리로 해결 가능한지 확인
- 마지막 릴리스 날짜 (1년 이상 ago = 위험)
- 라이선스 (MIT/Apache-2.0/BSD ✓, AGPL/GPL ?)
- 별점·이슈 수
- 대체재 비교 → PR 본문에 근거 작성

### 6.2 버전 고정
- production 의존성은 **정확한 버전** (caret/tilde 회피)
- 락 파일(`package-lock.json`, `uv.lock`, gradle lock) 커밋
- Renovate / Dependabot으로 주기적 업데이트

---

## 7. 파일·디렉토리

- 한 파일 한 책임
- 줄 수 가이드: 200줄 이내 권장, 400줄 초과 시 분할 검토
- 파일명은 export 하는 핵심 식별자와 일치 (PascalCase 컴포넌트는 PascalCase 파일명)

---

## 8. 매직 넘버·문자열

```java
// 나쁨
if (session.questionCount() >= 10) {...}

// 좋음
private static final int DEFAULT_MAX_QUESTIONS = 10;
if (session.questionCount() >= DEFAULT_MAX_QUESTIONS) {...}
```

상수는 가장 가까운 도메인 클래스에 위치. 전역 `Constants`는 회피.

---

## 9. Boolean Trap

```java
// 나쁨
public Session create(User user, boolean online, boolean technical) {...}

// 좋음
public Session create(User user, SessionMode mode, InterviewType type) {...}
```

---

## 10. 불변성 (Immutability)

- 가능한 한 불변 객체 (Java `record`, Kotlin `data class`, TS `readonly`)
- 컬렉션은 불변 또는 방어적 복사
- 예외: 성능 임계 경로

---

## 11. 의존성 주입

- 생성자 주입 (필드 주입 금지)
- 인터페이스 + 구현체 분리는 **테스트 더블이 필요할 때만**. 단일 구현이면 클래스 직접 사용.

---

## 12. 로깅

- 한국어 로그 메시지 OK (개발 팀 내부 사용)
- 단, 구조화 필드 키는 영어 (`userId`, `sessionId`)
- 민감 정보 절대 X — [observability.md §9](./observability.md)

---

## 13. Pull Request 사이즈

- 권장: 변경 라인 ≤ 400
- 초과 시 분할 검토 (리팩터 + 기능 분리, 도메인별 분리)
- 단일 commit ≠ 단일 PR. 한 PR에 의미 단위 commit 여러 개 OK.
