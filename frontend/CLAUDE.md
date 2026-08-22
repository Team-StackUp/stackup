# Frontend — Claude 컨텍스트

> StackUp 프론트엔드. **React 19 + TypeScript 5.9 + Vite 8** 기반 SPA. 구조는 **FSD (Feature-Sliced Design)**.

상위 컨텍스트: [`/CLAUDE.md`](../CLAUDE.md) · 횡단 관심사: [`/docs/`](../docs/README.md)

---

## 1. 기술 스택 (현재)

| 영역 | 기술 | 버전 |
|------|------|------|
| Framework | React | 19.x |
| Build | Vite | 8.x |
| Language | TypeScript | 5.9.x |
| Lint | ESLint 9 (flat config) + Prettier | |
| Module | ESM (`"type": "module"`) | |
| Router | React Router | 7.x (도입) |
| Server State | TanStack Query | 5.x (도입) |
| Styling | Tailwind CSS v4 (`@theme` 토큰) | 4.x (도입) |
| API 타입 | openapi-typescript → `shared/api/generated.ts` | (도입, §7.1) |
| 테스트 | Vitest + Testing Library (jsdom) | (도입) |

### 미정 (도입 시점에 결정)
- Form: React Hook Form + Zod (현재 controlled `useState` 검증으로 충분 — 폼 복잡도 증가 시 도입)
- E2E: Playwright

> 신규 의존성 추가 시 [`/docs/coding-conventions.md §6`](../docs/coding-conventions.md) 절차를 따르고 본 표를 갱신.

---

## 2. 디렉토리 구조 (FSD)

```
frontend/
├── public/
├── src/
│   ├── app/             # 앱 부트스트랩 (providers, router, styles)
│   │   ├── providers/
│   │   ├── router/
│   │   └── styles/
│   ├── pages/           # 라우트 단위 페이지
│   │   ├── Login/
│   │   ├── Workspace/
│   │   ├── Interview/
│   │   └── History/
│   ├── features/        # 기능 단위 (사용자 행동)
│   │   ├── auth/
│   │   ├── resume/
│   │   ├── interview/
│   │   └── feedback/
│   ├── domain/          # 도메인 모델·비즈니스 규칙
│   │   ├── user/
│   │   ├── session/
│   │   └── rag/
│   ├── shared/          # 도메인 비종속 재사용
│   │   ├── ui/          # 디자인 시스템 컴포넌트
│   │   ├── api/         # 자동 생성 타입, axios 래퍼
│   │   ├── hooks/
│   │   ├── lib/         # AsyncBoundary 등
│   │   ├── utils/
│   │   └── i18n/
│   └── assets/
├── index.html
├── vite.config.ts
├── tsconfig.json
└── eslint.config.js
```

각 슬라이스 디렉토리에 자체 `CLAUDE.md` 가 있으므로 작업 시 가장 가까운 것을 먼저 읽는다.

---

## 3. FSD 의존성 규칙 (엄격)

```
app  ─→  pages  ─→  features  ─→  domain  ─→  shared
```

- 화살표는 **import 가능 방향**. 역방향 import는 ESLint로 차단한다.
- 같은 레이어 내 슬라이스 간 import 금지 (예: `features/auth` → `features/resume` ✗).
  - 공통화가 필요하면 한 단계 아래(`domain` 또는 `shared`)로 추출.
- `pages`가 `pages` import도 금지 (라우터에서 lazy import만 허용).

### 예외
- `app/providers`는 `pages`를 import하지 않지만 `features/*`의 store/provider를 wrap할 수 있다.
- 타입(`type`-only import)은 의존성 규칙에서 제외 (런타임 의존이 없으므로).

---

## 4. 슬라이스 표준 구조

각 슬라이스(`features/{name}`, `domain/{name}` 등)는 다음 구조를 권장:

```
features/auth/
├── ui/                 # 컴포넌트
├── model/              # store, hooks, 상태 로직
├── api/                # 이 feature가 호출하는 API
├── lib/                # 슬라이스 내부 유틸
└── index.ts            # public API (외부에서 import 가능한 것만 export)
```

**Public API 규칙**: 슬라이스 외부에서는 항상 `index.ts`로 import.
```tsx
// 좋음
import { LoginButton, useAuth } from '@/features/auth';

// 나쁨 (내부 경로 직접 참조)
import { LoginButton } from '@/features/auth/ui/LoginButton';
```

---

## 5. 페이지 라우팅

라우터 결정 전이라도 다음 규칙 유지:
- 라우트 정의는 `app/router/`에 집중
- 각 페이지는 `pages/{Name}/index.ts`에서 default export
- 페이지는 **layout + composition만** 담당. 비즈니스 로직은 features로.

예상 라우트 (Phase 1):
```
/                         → / (redirect to /workspace or /login)
/login                    → pages/Login
/auth/callback            → OAuth 콜백 처리
/workspace                → pages/Workspace (이력서·레포 관리)
/sessions/new             → pages/Interview (세션 설정)
/sessions/:id             → pages/Interview (세션 진행)
/sessions/:id/feedback    → pages/Interview (피드백)
/history                  → pages/History
/history/:id              → pages/History (상세)
```

---

## 6. 비동기 데이터 처리

`shared/lib/AsyncBoundary` 사용. props 이름은 `pendingFallback` / `rejectedFallback`:
```tsx
<AsyncBoundary
  pendingFallback={<ResumeListSkeleton />}
  rejectedFallback={({ error, reset }) => (
    <ErrorState error={error} onRetry={reset} />
  )}
>
  <ResumeList />
</AsyncBoundary>
```

서버 상태는 TanStack Query 도입 후 `useSuspenseQuery`로 일원화 (Suspense + Error Boundary와 자연 통합).

---

## 7. API 호출

### 7.1 자동 생성 타입
- Backend OpenAPI → `shared/api/generated.ts` (`openapi-typescript`)
- 빌드 스크립트 (도입 시):
  ```json
  "openapi": "openapi-typescript http://localhost:8080/api/v3/api-docs -o src/shared/api/generated.ts"
  ```

### 7.2 axios/fetch 래퍼
- `shared/api/client.ts` 단일 클라이언트
- 기본 헤더: `Authorization`, `X-Trace-Id`(클라이언트 생성)
- 401 응답 시 refresh → 원 요청 재시도 (interceptor)
- 에러는 표준 에러 코드 ([`/docs/api-conventions.md §5`](../docs/api-conventions.md)) 기반 분기

### 7.3 features의 API 호출
- 각 feature는 자체 `api/` 폴더에서 query/mutation 정의
- 컴포넌트는 `useXxxQuery`, `useXxxMutation` 훅으로만 호출

---

## 8. 디자인 시스템 연동

- 토큰: `app/styles/tokens.css` (CSS variables)
- 컴포넌트: `shared/ui/{Component}/`
- 상세 토큰·인벤토리: [`/docs/design-system.md`](../docs/design-system.md)

**원칙**: 컴포넌트에서 색상·간격·타이포그래피는 토큰만 참조. 하드코딩 금지.

### 8.1 SEED Design 채택 범위 (2026-08)

컬러·radius·shadow 는 당근 **SEED Design** 토큰을 참조한다(Apache-2.0).

```
app/styles/index.css 로드 순서 (순서가 의미를 가진다)
  tailwindcss → @seed-design/css/base.css → @seed-design/tailwind4-theme
  → seed-overrides.css → tokens.css → global.css
```

- **채택**: 컬러 · radius(SEED r1~r6) · shadow(s1~s3)
- **미채택**: 타이포그래피(SEED 의 t1~t14 는 앱 UI 스케일 — 우리 디스플레이 스케일과 목적이 달라 자체 유지), 컴포넌트(`@seed-design/react` 미설치 — 모바일 지향 컴포넌트가 많아 우리 `shared/ui` 유지)
- **브랜드 리테마**: SEED 는 `*-brand` 를 팔레트 경유로 정의하므로(`bg-brand-solid: var(--palette-carrot-600)`), `seed-overrides.css` 에서 brand 시맨틱만 `blue` 팔레트로 돌렸다. 팔레트 자체는 건드리지 않는다.
- 우리 alias 이름은 유지하고 값만 `var(--seed-color-*)` 로 매핑했다 — SEED 이름(`text-fg-neutral`, `bg-layer-default` …)도 그대로 쓸 수 있다(두 네임스페이스는 이름이 겹치지 않는다).

**주의 — 배경용과 텍스트용 브랜드 토큰은 다르다.**
다크에서 요구가 정반대(solid 배경은 진해야, 브랜드 텍스트는 밝아야)라 한 토큰으로 만족할 수 없다.

| 용도 | 토큰 |
|---|---|
| 버튼·보더 배경 | `bg-primary` / `border-primary` |
| 브랜드 텍스트 | `text-primary-fg` |
| **고정 흰 표면 위** 브랜드 텍스트 | `text-primary` (양 모드 모두 흰 배경 대비 AA) |

### 8.2 다크모드

`shared/lib/color-mode` 가 `<html>` 의 SEED 속성을 관리한다.

```
data-seed-color-mode        = system | light-only | dark-only   (localStorage 영속)
data-seed-user-color-scheme = light | dark                      (system 일 때 matchMedia 로 우리가 설정)
```

`main.tsx` 가 첫 페인트 전에 적용한다(다크 사용자에게 라이트 화면이 번쩍이지 않게).
SEED 팔레트 블록에는 `prefers-color-scheme` 미디어쿼리가 없어서, `system` 모드에서도
`data-seed-user-color-scheme` 를 직접 써 주지 않으면 OS 설정이 반영되지 않는다.

**색을 쓸 때**: 반드시 시맨틱 토큰(`bg-surface`, `text-fg-muted` …). `sage-*` 는
**항상 어두워야 하는 표면**(푸터·다크 패널) 전용이며, 표면/본문 의미로 쓰면 다크에서 깨진다.

---

## 9. 미디어 (음성/영상)

- 마이크: `navigator.mediaDevices.getUserMedia({ audio: true })`
- WebRTC: RealTime 서버와 SDP/ICE 교환
- 코덱: 음성은 Opus, 영상은 VP9
- 권한 거부 시 텍스트 입력 fallback (US-21 AC-05)
- 구현 위치: `features/interview/lib/media/`

---

## 10. 실시간 이벤트

- **SSE + WebSocket 병행** — 작업 상태 푸시(분석·피드백)는 SSE, 라이브 면접 메시지는 WS(`features/interview/model/useInterviewSocket.ts`). (루트 CLAUDE.md §8 과 동일)
- 구현: `shared/hooks/useEventStream.ts` — 자동 재연결(지수 백오프) + 연결 상태 반환. 워크스페이스는 단절(closed) 시 배너 표시 + 목록 쿼리 5s 폴백 폴링(`useAnalysisFallbackPolling`)
- 미디어 스트림(음성/영상)만 WebRTC: `features/interview/lib/media/`
- 이벤트 스펙: [`/docs/event-stream.md`](../docs/event-stream.md)

---

## 11. 환경변수

`VITE_` 접두 필수 (런타임 노출).
`.env.local` (커밋 X) 사용. 자세한 키 목록: [`/docs/environment.md §5`](../docs/environment.md).

```typescript
// shared/config/env.ts
export const env = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
  SSE_BASE_URL: import.meta.env.VITE_SSE_BASE_URL,
  GITHUB_OAUTH_CLIENT_ID: import.meta.env.VITE_GITHUB_OAUTH_CLIENT_ID,
} as const;
```

---

## 12. 코드 스타일

### TypeScript
- `strict: true` 유지
- `any` 금지 (실수 발견 시 `unknown` + type guard로 전환)
- 함수형 컴포넌트만 (`React.FC` 사용 안 함, props는 명시적 interface/type)

### React
- 키 prop은 의미 있는 ID (배열 index 회피)
- side-effect는 `useEffect` 최소화 — 가능하면 server state로 위임
- `useState` 초기화에 비싼 연산 → lazy initialization

### import 정렬 (ESLint 또는 Prettier 플러그인으로 강제)
1. React / 외부
2. `@/app`, `@/pages`, `@/features`, `@/domain`, `@/shared`
3. 상대경로
4. CSS / asset

### 주석
- 기본 주석 없음. WHY일 때만. ([`/docs/coding-conventions.md §3`](../docs/coding-conventions.md))

---

## 13. 테스트

- 단위: Vitest + Testing Library
- E2E: Playwright (`frontend/e2e/`)
- MSW로 API mocking
- 핵심 시나리오: [`/docs/testing-strategy.md §3`](../docs/testing-strategy.md)

---

## 14. 빌드·배포

```bash
npm run dev       # 로컬 개발 서버 (default :5173)
npm run build     # 타입 체크 + 프로덕션 빌드 (dist/)
npm run preview   # 빌드 결과 로컬 서빙
npm run lint      # ESLint
```

배포: CloudFront + S3 정적 호스팅 (Phase 2 운영 단계).

---

## 15. 자주 묻는 작업

| 작업 | 위치 |
|------|------|
| 새 페이지 추가 | `pages/{Name}/`, 라우터 등록 |
| 새 도메인 기능 | `features/{name}/`, public API 정의 |
| 새 컴포넌트 (도메인 비종속) | `shared/ui/{Name}/` |
| 새 API 엔드포인트 사용 | OpenAPI 재생성 → `features/{name}/api/` |
| 토큰 추가/변경 | `app/styles/tokens.css` + `/docs/design-system.md` 갱신 |
| 새 라우트 | `app/router/` |

---

## 16. 현재 상태 메모 (2026-06 기준)

- 라이브러리 결정 완료: React Router v7, TanStack Query v5, Tailwind v4, Vitest+Testing Library (§1).
- 디자인 토큰 `app/styles/tokens.css` 생성됨. `shared/ui` 프리미티브: StatusBadge, Button, Spinner, TextArea, RadioCardGroup, Stepper.
- **텍스트 면접 세션 구현됨**: `/sessions/new`(생성 설정), `/sessions/:id`(라이브 진행). `features/interview` + `domain/session`. 라이브는 WS(`useInterviewSocket`) 알림 + `GET messages` 쿼리 본문 소유 + 낙관적 답변 (`useLiveInterview`). TTS 재생·음성 답변(RT3)은 범위 밖.
- **OpenAPI 타입 파이프라인**: 백엔드가 `OpenApiSpecExportTest`로 `backend/openapi.json` 산출·커밋 → 프론트 `npm run openapi`(= `openapi-typescript ../backend/openapi.json …`)로 `shared/api/generated.ts` 생성. 프론트는 백엔드 런타임에 무의존. 계약 변경 시 백엔드 테스트 재실행으로 갱신.
