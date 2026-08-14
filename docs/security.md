# 보안 가이드라인

> 인증·인가·암호화·개인정보 처리 규약. 모든 신규 기능은 이 문서 체크리스트를 통과해야 한다.

---

## 1. 인증 (Authentication)

### 1.1 GitHub OAuth 흐름
1. Frontend → `POST /api/auth/github` → state 발급 + GitHub 인증 URL 응답
2. Frontend가 GitHub로 리다이렉트
3. GitHub → callback URL?code=xxx&state=yyy
4. Core Server: state 검증 → GitHub Token Exchange → access_token 획득
5. users UPSERT + JWT access/refresh 발급
6. refresh_token은 **HttpOnly + Secure + SameSite=Strict** 쿠키로 전달
7. access_token은 응답 본문 → Frontend가 메모리 또는 localStorage에 보관

> **state 검증 누락 = CSRF 취약**. PostgreSQL `oauth_states` 테이블 (state PK + expires_at + 사용 후 DELETE)에 5분 TTL로 저장하고 검증. 또는 stateless 방식으로 HMAC-서명된 state 사용.

### 1.1.1 Google OAuth 흐름

경로만 다르고 단계는 GitHub 과 동일하다 — `POST /api/auth/google` → `GET /api/auth/google/callback`,
PKCE(S256) + `oauth_states` 검증 + 같은 JWT/refresh 쿠키 발급.

GitHub 과 다른 점:

| 항목 | GitHub | Google |
|---|---|---|
| scope | `read:user user:email repo` | `openid email profile` (레포 권한 없음) |
| 계정 식별자 | `github_id` | OIDC `sub` (`google_id`) |
| 저장 토큰 | access_token 을 AES 암호화해 보관(레포 조회에 재사용) | **보관하지 않음** — 신원 확인 뒤 버린다 |
| 사용 가능 기능 | 전체 | 레포지토리 분석 제외 |

- **`oauth_states` 는 provider 를 함께 검증한다.** state 테이블이 provider 별로 나뉘지 않아,
  검증하지 않으면 GitHub 용 state 를 Google 콜백에 넘겨 흐름을 섞을 수 있다.
- **id_token 서명 검증 대신 userinfo 호출**을 쓴다. 토큰을 브라우저가 아니라 서버가 TLS 로 직접
  받아오므로 출처가 보장된다(OIDC Core §3.1.3.7). JWKS 캐싱·키 롤오버를 떠안지 않는다.
- **이메일이 같아도 GitHub 계정과 자동 병합하지 않는다.** 이메일은 소유가 바뀔 수 있어 신원의
  근거로 삼으면 계정 탈취 경로가 된다. 계정 연결은 로그인한 상태에서 명시적으로 하는 별도 기능.
- Google 계정이 GitHub 전용 기능을 호출하면 `AUTH_GITHUB_NOT_LINKED`(409) — 복호화 단계에서
  NPE 가 500 으로 새어나가지 않도록 `InternalGithubTokenService` 에서 먼저 막는다.

### 1.2 JWT 정책
| 토큰 | TTL | 저장 위치 | 갱신 |
|------|-----|-----------|------|
| access_token | 15분 | Frontend 메모리 (또는 localStorage) | refresh로 재발급 |
| refresh_token | 14일 | HttpOnly 쿠키 + DB(해시) | 사용 시 회전 (rotation) |

- access_token에 포함: `userId`, `iat`, `exp`, `traceId(없음)`
- refresh_token: 클라이언트에는 평문, **DB에는 해시(SHA-256)만** 저장
- 재사용 감지: 한 번 사용한 refresh_token이 다시 들어오면 모든 토큰 revoke

---

## 2. 인가 (Authorization)

- 모든 `/api/**`는 인증 필수 (예외: `/api/auth/*`, `/api/system/health`)
- 리소스 소유권 검증: `userId`가 일치해야 접근 허용
  ```java
  @PreAuthorize("@sessionAccessChecker.canAccess(#id, principal)")
  public SessionDetail getSession(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {...}
  ```
- 다른 사용자의 리소스 접근 시 → **403 Forbidden** (404 아님, 정보 누설 방지를 위해서는 404 권장하나 본 프로젝트는 단순화 위해 403)
- 동의 미완료 사용자: 이력서 업로드/면접 세션 진입 시 403 + `AUTH_CONSENT_REQUIRED`

---

## 3. 암호화

### 3.1 GitHub Access Token
- **저장 시 AES-256-GCM 암호화** 필수
- 키는 환경변수 `ENCRYPTION_KEY` (32바이트 base64)
- 컬럼명에 `encrypted_` 접두 (`encrypted_github_access_token`)
- 복호화는 사용 직전에만, 메모리 보관 최소화

```java
public class GithubTokenCipher {
    public String encrypt(String plaintext) { /* AES-GCM */ }
    public String decrypt(String ciphertext) { /* AES-GCM */ }
}
```

### 3.2 비밀 정보 운반
- 환경변수 (`.env`) — git ignore
- 운영 환경: AWS Secrets Manager 또는 K8s Secret
- **절대 금지**: 평문 커밋, Slack/이메일 전송, 로그 출력

### 3.3 전송 (TLS)
- 모든 외부 통신 HTTPS
- 내부 컨테이너 간 통신은 같은 VPC/네트워크 내에서 평문 OK (Phase 1)
- Phase 2 운영: mTLS 검토

---

## 4. 입력 검증

### 4.1 Validation 위치
| 계층 | 검증 항목 |
|------|-----------|
| Frontend | UX (즉각 피드백) |
| Controller | DTO `@Valid`, `@NotNull`, `@Size` |
| Service | 비즈니스 규칙 (상태 전이, 권한) |
| Database | CHECK 제약, FK |

### 4.2 파일 업로드
- MIME type **검증 필수** (`application/pdf` only)
- magic byte 검증 (확장자만 신뢰 X)
- 파일 크기 제한 (resume: 20MB, audio: 50MB)
- 파일명은 서버에서 UUID로 재생성 (Path Traversal 방지)
- 업로드 직후 안티바이러스 스캔 — Phase 2 검토

### 4.3 SQL Injection
- **Native Query 작성 시 항상 Prepared Statement** (named parameter)
- 동적 정렬·필터는 화이트리스트 검증
- pgvector 검색도 마찬가지

### 4.4 XSS
- React는 기본적으로 escape 처리
- `dangerouslySetInnerHTML` 사용 금지 (사용해야 한다면 DOMPurify로 sanitize)
- Markdown 렌더링도 sanitize (`react-markdown` + remark-rehype 안전 옵션)

### 4.5 CSRF
- access_token 기반 인증 → CSRF 위험 낮음 (refresh 쿠키만 SameSite=Strict로 차단)
- POST/PATCH/DELETE는 `Authorization` 헤더 필수 (쿠키 기반 인증 X)

---

## 5. 개인정보 처리

### 5.1 동의 (US-03)
- 최초 로그인 시 필수 동의 (`TOS`, `PRIVACY`)
- 동의 없이는 이력서 업로드, 면접 세션 진입 차단
- `user_consents` 테이블에 IP, 동의 시각, 버전 기록
- 동의 철회 → `revoked_at` 기록 + 관련 데이터 삭제 절차 안내

### 5.2 민감정보
이력서 PDF에 포함될 수 있는 민감정보:
- 이름, 연락처, 이메일, 주소, 학력, 경력, 사진
- LLM에 전송 시 자동 마스킹은 **현재 Out of Scope** (Backlog US-09 명시)
- 추후 도입 시: 정규식 + NER 기반 마스킹 (이메일/전화번호/주민번호 패턴)

### 5.3 회원 탈퇴 (US-04)
- soft delete (`is_deleted = TRUE`)
- refresh_tokens 전부 revoke
- 30일 후 hard delete + S3 객체 삭제 (Phase 2 자동화)
- 동일 GitHub 계정 재가입 시 신규 사용자로 생성 (기존 데이터 복구 X)

### 5.4 데이터 최소 수집
- GitHub OAuth 시 요청 scope 최소화: `read:user`, `user:email`, `repo` (private 분석 위해)
- Google OAuth 는 `openid email profile` 만 — 로그인 신원 외 권한을 받지 않는다
- email 옵션 (사용자가 GitHub에서 비공개 설정 시 NULL 허용)

---

## 6. 의존성 관리

### 6.1 취약점 스캔
- Backend: `gradle dependencyCheck` (OWASP Dependency-Check) — CI에서 자동 실행
- Frontend: `npm audit` — 매 PR
- Python: `pip-audit` — CI

### 6.2 라이선스 호환성
- AGPL/GPL 의존성 검토 (서비스가 SaaS이므로 신중)
- MIT/Apache-2.0/BSD 우선

---

## 7. 로깅 시 주의

**절대 로그에 남기지 않는다**:
- 비밀번호, 토큰, API 키
- 이력서 본문 (요약만 OK)
- 사용자 답변 본문 (디버그 모드 한정, 운영은 마스킹)
- 신용카드 등 결제 정보 (해당사항 없음)

마스킹 대상은 `LoggingMasker` 유틸로 일괄 처리.

---

## 8. 의존성 격리

- Core Server만 PG 직접 접근 → AI 서버가 침해되어도 DB 직접 노출 X
- AI Server의 LLM API 키는 AI 서버 환경변수에만 (Core가 모름)
- 각 서비스는 최소 권한 원칙 (PG user, S3 IAM, RabbitMQ user)

---

## 9. Rate Limiting

| 엔드포인트 | 제한 |
|------------|------|
| `/api/auth/*` | IP당 10req/min |
| `/api/resumes` POST | user당 10건/hour |
| `/api/sessions` POST | user당 5건/hour |
| 그 외 | user당 60req/min |

구현: Spring Cloud Gateway / Bucket4j / Nginx limit_req

---

## 10. 신규 기능 보안 체크리스트

PR 머지 전 확인:

- [ ] 인증·인가 적용 (anonymous endpoint는 명시적 표시)
- [ ] 입력 DTO에 `@Valid` 적용
- [ ] 외부 API 호출에 timeout / retry / circuit breaker
- [ ] 비밀 정보가 코드/로그에 노출되지 않음
- [ ] DB 접근은 ORM 또는 Prepared Statement
- [ ] 새 환경변수는 `.env.example` 업데이트
- [ ] OWASP Top 10 적용성 검토 (Injection, Broken Auth, Sensitive Data Exposure 등)
- [ ] 변경사항이 동의 항목·개인정보 처리 범위에 영향 시 `user_consents` 정책 갱신
