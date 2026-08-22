# REST API 설계 규약

> 모든 Core Server REST API는 본 문서의 규약을 따른다. OpenAPI 스펙은 springdoc-openapi가 자동 생성하고, 프론트는 `openapi-typescript`로 타입을 생성한다.

---

## 1. URL 구조

```
/api/{resource}                  # 컬렉션
/api/{resource}/{id}             # 단일 리소스
/api/{resource}/{id}/{sub}       # 서브 리소스
/api/{resource}/{id}:{action}    # 액션 (PATCH 대신 명시적 동사)
```

- 리소스명은 **복수형, kebab-case 금지, snake_case 금지** → `repositories`, `interview-sessions` 대신 `sessions` (짧고 명확하게)
- 액션이 RESTful 동사로 표현 안 될 때만 `:start`, `:end` 형식 사용

### 표준 매핑

| 동작 | 메서드 | 경로 예시 |
|------|--------|-----------|
| 컬렉션 조회 | GET | `/api/sessions` |
| 단건 조회 | GET | `/api/sessions/{id}` |
| 생성 | POST | `/api/sessions` |
| 부분 수정 | PATCH | `/api/sessions/{id}` |
| 전체 교체 | PUT | (거의 사용하지 않음) |
| 삭제 (soft) | DELETE | `/api/sessions/{id}` |
| 액션 트리거 | PATCH | `/api/sessions/{id}/start`, `/api/sessions/{id}/end` |

---

## 2. 주요 API 그룹

### 2.1 인증
```
POST   /api/auth/github            GitHub OAuth 시작 (URL 발급)
GET    /api/auth/github/callback   OAuth 콜백 처리
POST   /api/auth/refresh           토큰 갱신
DELETE /api/auth/logout            로그아웃 (refresh token revoke)
```

### 2.2 사용자
```
GET    /api/users/me               내 정보
PATCH  /api/users/me               내 정보 수정 (제한적)
DELETE /api/users/me               회원 탈퇴 (soft)
GET    /api/users/me/stats         내 통계 (점수 추이)
GET    /api/users/me/consents      동의 이력
POST   /api/users/me/consents      동의 제출
```

### 2.3 자료
```
GET    /api/repositories           내가 등록한 레포 목록
GET    /api/repositories/github    GitHub의 내 전체 레포 (등록 가능 후보)
POST   /api/repositories           등록
GET    /api/repositories/{id}      상세 (분석 상태 포함)
DELETE /api/repositories/{id}      소프트 삭제
POST   /api/repositories/{id}/reanalyze   재분석 요청

POST   /api/resumes                업로드
GET    /api/resumes                목록
GET    /api/resumes/{id}           상세
DELETE /api/resumes/{id}           소프트 삭제
POST   /api/resumes/{id}/reanalyze 재분석

GET    /api/documents              분석 문서 목록
GET    /api/documents/{id}         분석 문서 상세 (S3 URL 포함)
GET    /api/documents/{id}/content 분석 원문(마크다운) 프록시 — presigned 는 내부 호스트라 Core 중계
```

### 2.4 면접 세션
```
POST   /api/sessions               생성
GET    /api/sessions               히스토리 목록
GET    /api/sessions/{id}          상세 (메시지/피드백 포함)
PATCH  /api/sessions/{id}          메모/제목 수정
PATCH  /api/sessions/{id}/start    시작 (READY → IN_PROGRESS)
PATCH  /api/sessions/{id}/end      종료
DELETE /api/sessions/{id}          소프트 삭제

GET    /api/sessions/{id}/messages 메시지 목록
POST   /api/sessions/{id}/messages 메시지 추가 (RealTime → Core 전용)

GET    /api/sessions/{id}/feedback 피드백 조회
```

### 2.5 시스템
```
GET    /api/system/health          헬스체크 (PG/MQ/S3/AI 상태)
GET    /api/system/version         버전 정보
```

### 2.6 내부 API (서비스 간 호출 전용)

`/api/internal/*` prefix. **외부에서 호출 금지** — Spring Security에서 외부 패킷을 차단하고 `X-Internal-API-Key` 헤더로 검증. AI Server와 RealTime Server만 호출자.

```
GET  /api/internal/users/{userId}/github-token         AI → Core
PUT  /api/internal/documents/{documentId}/embeddings   AI → Core
POST /api/internal/embeddings/search                   AI → Core
POST /api/internal/ai-logs                             AI → Core
```

자세한 요청·응답 스키마와 인증 규약은 §10 내부 API 부록 참조.

---

## 3. 요청 규약

### 3.1 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Authorization: Bearer {token}` | 인증 필요 시 | JWT access token |
| `X-Trace-Id` | 권장 | 클라이언트가 부여하지 않으면 Gateway가 부여 |
| `Content-Type: application/json` | POST/PATCH | multipart/form-data는 파일 업로드 시만 |

### 3.2 페이지네이션

```
GET /api/sessions?page=0&size=20&sort=createdAt,desc
```

- 0-based page
- `size` 최대 100 (초과 시 100으로 clamp)
- 정렬: `{field},{asc|desc}` 형식, 다중 정렬은 `&sort=` 반복

### 3.3 필터링

```
GET /api/sessions?status=COMPLETED&jobCategory=BACKEND&from=2026-01-01&to=2026-04-30
```

- 날짜는 ISO 8601 (`YYYY-MM-DD` 또는 RFC 3339)
- 다중 값은 콤마: `?status=COMPLETED,CANCELLED`

---

## 4. 응답 규약

### 4.1 성공 (2xx)

**단건**:
```json
{
  "id": 42,
  "title": "백엔드 모의면접 #3",
  "status": "COMPLETED",
  "createdAt": "2026-04-25T14:30:00Z"
}
```

**컬렉션 (페이지)**:
```json
{
  "content": [ {...}, {...} ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "first": true,
  "last": false
}
```

### 4.2 에러 (4xx, 5xx)

표준 형식:
```json
{
  "code": "RESUME_INVALID_FILE_TYPE",
  "message": "PDF 파일만 업로드 가능합니다.",
  "traceId": "9f4e5b...",
  "timestamp": "2026-04-27T15:20:00Z",
  "details": {
    "uploadedType": "image/jpeg"
  }
}
```

| 필드 | 설명 |
|------|------|
| `code` | 도메인 에러 코드 (대문자 SNAKE_CASE) — 클라이언트 분기 기준 |
| `message` | 사용자에게 노출 가능한 한국어 메시지 |
| `traceId` | 로그 추적용, 사용자 문의 시 활용 |
| `details` | 추가 컨텍스트 (validation 실패 필드 등) |

### 4.3 표준 HTTP 상태 코드

| 상황 | 코드 |
|------|------|
| 성공 (조회/수정) | 200 |
| 생성 성공 | 201 (`Location` 헤더 포함) |
| 비동기 처리 시작 | 202 |
| 본문 없음 (DELETE 등) | 204 |
| 잘못된 요청 (validation 실패) | 400 |
| 인증 누락/만료 | 401 |
| 권한 부족 | 403 |
| 리소스 없음 | 404 |
| 충돌 (중복 등록 등) | 409 |
| 처리 가능하지만 의미적 오류 | 422 |
| 서버 오류 | 500 |
| 외부 의존성 장애 (LLM API 등) | 502 / 503 |

---

## 5. 에러 코드 카탈로그

> 신규 추가 시 본 카탈로그에 반드시 등록. 같은 코드를 두 도메인에서 사용 금지.

```
# 인증 (AUTH_*)
AUTH_INVALID_TOKEN          401  토큰 형식 오류
AUTH_EXPIRED_TOKEN          401  만료
AUTH_REVOKED_TOKEN          401  revoke됨
AUTH_GITHUB_OAUTH_FAILED    401  GitHub OAuth 실패
AUTH_CONSENT_REQUIRED       403  필수 동의 미완료

# 사용자 (USER_*)
USER_NOT_FOUND              404
USER_ALREADY_DELETED        410

# 이력서 (RESUME_*)
RESUME_INVALID_FILE_TYPE    400  PDF 외 업로드
RESUME_FILE_TOO_LARGE       400  크기 초과
RESUME_EMPTY_FILE           400
RESUME_NOT_FOUND            404
RESUME_IN_USE               409  활성 세션에서 사용 중

# 레포 (REPO_*)
REPO_NOT_FOUND              404
REPO_ALREADY_REGISTERED     409
REPO_GITHUB_API_FAILED      502
REPO_PRIVATE_NO_ACCESS      403

# 분석 (DOC_*)
DOC_NOT_ANALYZED            422  ANALYZED 상태 아님
DOC_ANALYSIS_FAILED         500
DOC_NOT_FOUND               404

# 세션 (SESSION_*)
SESSION_INVALID_STATE       422  상태 전이 불가
SESSION_MAX_REACHED         422  최대 질문/시간 도달
SESSION_NOT_FOUND           404
SESSION_FORBIDDEN           403  타인 세션 접근

# 피드백 (FEEDBACK_*)
FEEDBACK_NOT_READY          404  아직 생성 중 (폴링/SSE 대기 지속)
FEEDBACK_GENERATION_FAILED  404  생성 실패 마커 존재 — 대기 중단, 재생성 유도. details.retriable(boolean) 동봉
FEEDBACK_NOT_FOUND          404  공유 토큰 무효
FEEDBACK_ALREADY_EXISTS     409  재생성 요청 시 이미 존재 (재조회 신호)

# 시스템 (SYS_*)
SYS_RATE_LIMITED            429
SYS_DEPENDENCY_DOWN         503  RabbitMQ/AI/LLM 다운
SYS_INTERNAL_ERROR          500
```

---

## 6. 멱등성 (Idempotency)

POST 요청 중 결제·메시지 발행 등 **중복 처리 위험**이 있는 경우:

```
POST /api/sessions
Idempotency-Key: 8b3c5e2a-...
```

서버는 24시간 동안 같은 키로 들어온 요청에 대해 동일 응답을 반환.

> Phase 1에서는 결제 없으므로 필수는 아니나, AI 작업 발행 API(`reanalyze` 등)에 적용 권장.

---

## 7. 버전 관리

- 본 문서 기준 v1. 모든 경로에 `/api/v1/...` 접두는 **사용하지 않음** (Breaking Change 발생 시점에 도입).
- Breaking Change 정책: 신규 경로 추가 + 구 경로는 최소 1개 phase 유지 후 제거.

---

## 8. OpenAPI 자동 생성

### Backend
- springdoc-openapi `application.properties`:
  ```
  springdoc.api-docs.path=/api/v3/api-docs
  springdoc.swagger-ui.path=/api/swagger-ui.html
  ```
- `@Operation`, `@Schema` 애노테이션으로 응답 예시 작성

### Frontend
- `openapi-typescript` 으로 `frontend/src/shared/api/generated.ts` 생성
- 빌드 스크립트:
  ```bash
  npx openapi-typescript http://localhost:8080/api/v3/api-docs -o src/shared/api/generated.ts
  ```
- 변경 시 PR에 포함

---

## 9. 비동기 작업 응답 패턴

POST/PATCH가 즉시 완료되지 않는 경우 (AI 분석 트리거 등):

```http
HTTP/1.1 202 Accepted
{
  "taskId": "doc-analyze-42",
  "status": "QUEUED",
  "statusUrl": "/api/documents/42",
  "streamUrl": "/api/stream/documents/42"
}
```

클라이언트는:
1. SSE (`streamUrl`) 우선 구독 → 상태 푸시 수신
2. SSE 끊기면 `statusUrl` 폴링 fallback (5초 간격)

상세: [`event-stream.md`](./event-stream.md)

---

## 10. 내부 API 부록 (서비스 간 호출)

`/api/internal/*` endpoint는 다음 규약을 따른다.

### 10.1 인증
- 모든 요청에 `X-Internal-API-Key: {key}` 헤더 필수
- 키는 환경변수 `CORE_INTERNAL_API_KEY` (Core/AI/RealTime 동일 값)
- JWT 사용자 인증 불필요. 단 호출자가 신뢰 영역 안에 있어야 함 (Docker 내부망, K8s ClusterIP 등)
- 키 누락/불일치 → `401 Unauthorized`, body: `{ "code": "INTERNAL_AUTH_FAILED" }`

### 10.2 `GET /api/internal/users/{userId}/github-token`

분석 시점에 사용자 GitHub access token을 짧게 위임. AES-256으로 저장된 평문 token을 복호화해 반환 (메모리/로그/응답 body에만 노출, DB·이벤트엔벨로프에는 절대 동봉하지 않음).

**Path parameter**
| 이름 | 타입 | 설명 |
|------|------|------|
| `userId` | long | `users.id` |

**Response 200**
```json
{ "accessToken": "ghp_xxx..." }
```

**Response 404** — 사용자 없음 또는 token 누락 (`USER_NOT_FOUND`)
**Response 401/403** — 인증 실패 (`INTERNAL_AUTH_FAILED`)
**Response 5xx** — Core 일시 장애 (호출자는 retriable로 간주)

### 10.3 `PUT /api/internal/documents/{documentId}/embeddings`

`analyzed_documents` 한 건에 대한 chunk + embedding을 `document_embeddings` 테이블에 idempotent upsert. AI가 분석 결과를 callback 발행 직전에 호출한다.

**Path parameter**
| 이름 | 타입 | 설명 |
|------|------|------|
| `documentId` | long | `analyzed_documents.id` (Core가 publish 직전 미리 생성) |

**Request body**
```json
{
  "model": "gemini-embedding-001",
  "dim": 1536,
  "chunks": [
    { "chunkIndex": 0, "chunkText": "...", "embedding": [0.012, -0.003, ...] },
    { "chunkIndex": 1, "chunkText": "...", "embedding": [...] }
  ]
}
```

| 필드 | 타입 | 비고 |
|------|------|------|
| `model` | string | 임베딩 모델 ID (감사용) |
| `dim` | int | 임베딩 차원. DB 컬럼 차원과 일치해야 함 (불일치 → 400) |
| `chunks[].chunkIndex` | int | 0-base, 같은 document 내 unique |
| `chunks[].chunkText` | string | 원문 청크 (≤ N KB, 컬럼 길이 제한) |
| `chunks[].embedding` | float[] | 길이 == `dim` |

**Response 200**
```json
{ "upserted": 18 }
```

**Response 400** — `dim` 불일치, payload 검증 실패 (`EMBEDDING_BAD_REQUEST`)
**Response 404** — `analyzed_documents.id` 없음 (`DOCUMENT_NOT_FOUND`)
**Response 401/403** — `INTERNAL_AUTH_FAILED`
**Response 5xx** — Core 일시 장애 (retriable)

**Idempotency**
- 같은 `(documentId, chunkIndex)` 조합 재호출 시 row를 덮어씀 (INSERT ... ON CONFLICT UPDATE)
- 부분 재시도 시 누락 청크가 발생하지 않도록 AI는 전체 청크를 한 번에 보내는 것을 권장

### 10.4 `POST /api/internal/embeddings/search`

RAG 검색. AI가 질문 풀·꼬리질문·피드백 생성 시 컨텍스트 청크를 가져온다. `document_embeddings`에서 pgvector cosine topK — `queryText`가 주어지면 벡터 + full-text(BM25) RRF 하이브리드로 검색한다.

**Request body**
```json
{
  "queryEmbedding": [0.012, -0.003, ...],
  "queryText": "낙관적 락 동시성 제어",
  "documentIds": [12, 13],
  "topK": 5
}
```

| 필드 | 타입 | 비고 |
|------|------|------|
| `queryEmbedding` | float[] | 필수. 쿼리 임베딩 벡터 |
| `queryText` | string | 선택. 주어지면 하이브리드(RRF) 검색 |
| `documentIds` | long[] | 검색 범위 제한. 비어 있으면 전체 |
| `topK` | int | 양수. 미지정 시 5 |

**Response 200**
```json
{
  "hits": [
    { "documentId": 12, "chunkIndex": 3, "chunkText": "...", "distance": 0.18 }
  ]
}
```

**Response 400** — `queryEmbedding` 누락
**Response 401/403** — `INTERNAL_AUTH_FAILED`

> RAG 보강용이라 fatal 이 아니다 — AI(`core/client.py: search_embeddings`)는 호출 실패·non-2xx 를 빈 결과로 폴백하고 검색 없이 생성을 계속한다.

### 10.5 `POST /api/internal/ai-logs`

AI의 LLM 호출별 토큰·지연시간을 `ai_request_logs`에 기록 (US-30 관측성). LangChain 콜백(`observability/llm_logging_callback.py`)이 호출 완료·실패 시마다 발사한다.

**Request body**
```json
{
  "userId": 123,
  "sessionId": 99,
  "requestType": "generate.followup",
  "modelName": "gemini-3.1-flash",
  "inputTokens": 1820,
  "outputTokens": 210,
  "latencyMs": 1830,
  "status": "SUCCEEDED",
  "errorMessage": null
}
```

| 필드 | 타입 | 비고 |
|------|------|------|
| `requestType` | string | 필수 (NotBlank) |
| `status` | string | 필수 |
| 나머지 | — | 전부 nullable |

**Response 202** — 기록 큐잉 (본문 없음)
**Response 401/403** — `INTERNAL_AUTH_FAILED`

> Fire-and-forget — AI는 실패해도 raise 하지 않고 경고 로그만 남긴다(관측 실패가 본 작업을 막지 않게).
