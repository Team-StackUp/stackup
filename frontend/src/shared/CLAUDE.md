# `shared/` — 도메인 비종속 재사용 레이어

> 어떤 도메인에도 의존하지 않는 코드. UI 컴포넌트, 유틸, API 클라이언트.

상위: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 1. 디렉토리 구성

```
shared/
├── ui/        # 디자인 시스템 컴포넌트 (Button, Input, Modal, Badge, ...)
├── api/       # 자동 생성 타입, axios/fetch 클라이언트, 인터셉터
├── hooks/     # 도메인 비종속 훅 (useEventStream, useDebounce, ...)
├── lib/       # 추상화된 라이브러리 (AsyncBoundary, ErrorBoundary)
├── utils/     # 순수 유틸 함수 (date, format)
├── i18n/      # 번역 키
└── config/    # 환경 변수 wrapper
```

---

## 2. 의존성 규칙

```
shared/*  →  shared/*    ✓ (단 순환 금지)
shared/*  →  domain/*    ✗
shared/*  →  features/*  ✗
shared/*  →  pages/*     ✗
shared/*  →  app/*       ✗
```

shared는 **가장 아래** 레이어. 어떤 것도 import하지 않는다 (외부 라이브러리 제외).

도메인 개념이 들어가는 순간 → `domain/`으로 옮긴다.

---

## 3. shared vs domain 판별

| 케이스 | 위치 |
|--------|------|
| `Button` | shared/ui |
| `JobCategoryBadge` (직군별 색상) | features/* (도메인 의존) — 또는 domain/user/ui (드물게) |
| `formatDate(date, format)` | shared/utils |
| `formatSessionDuration(session)` | domain/session/lib (도메인 타입 필요) |
| `useDebounce` | shared/hooks |
| `useSessionTimer(session)` | features/interview |

**판단 기준**: 도메인 타입을 import하면 domain/features. 안 하면 shared.

---

## 4. shared/ui (디자인 시스템 컴포넌트)

별도 [`ui/CLAUDE.md`](./ui/CLAUDE.md) 참조.

---

## 5. shared/api

```
shared/api/
├── client.ts          # axios 인스턴스 (baseURL, interceptors)
├── auth.ts            # 토큰 inject, refresh 로직
├── errors.ts          # 에러 코드 enum, ApiError 클래스
├── generated.ts       # OpenAPI 자동 생성 타입 (커밋 X 또는 O 정책 결정)
└── index.ts
```

원칙:
- 서버 baseURL, 헤더, 인증, retry/refresh는 client에 집중
- 도메인 API 호출은 `features/{name}/api/` 에서 정의 (그 안에서 client 사용)
- 응답 타입은 `generated.ts` 우선, 없으면 도메인 타입으로 변환

---

## 6. shared/hooks (도메인 비종속만)

현재 구현:
- `useEventStream({ path, getToken, handlers })` — SSE 추상화 (지수 백오프 재연결, `StreamConnectionStatus` 반환)
- `workspaceStreamHealth` / `useAnalysisFallbackPolling` — 워크스페이스 분석 SSE 건강 상태 store + 단절 시 5s 폴백 폴링 스위치
- `useAnalysisProgress`, `useCopyToClipboard`
- `useQuestionRunner(questionIds, storageKey?)` — 질문을 한 개씩 넘기며 답을 적고 정답을 확인하는
  드릴 상태 기계. **연습 면접과 오답노트가 함께 쓰므로 여기 있다** — features 끼리는 서로
  import 할 수 없다(FSD §3). 질문 id 목록만 받고 질문을 어디서 얻는지는 모른다(도메인 비종속).
  `storageKey` 를 주면 답변 메모를 localStorage 에 남긴다(새로고침 유실 방지).

권장 후보:
- `useDebounce(value, ms)`
- `useThrottle(callback, ms)`
- `useMediaQuery(query)`
- `useLocalStorage<T>(key)`
- `useIsMounted()`
- `useClickOutside(ref, handler)`

도메인 의존이 들어가면 `features/{name}/model/`로.

---

## 7. shared/lib

추상화 컴포넌트 (라이브러리 wrap):
- `AsyncBoundary/` — Suspense + ErrorBoundary 합성 (이미 구현됨)
- `ErrorBoundary/` — 클래스 컴포넌트 (이미 구현됨)
- `Portal/` — Modal/Toast 등에서 사용
- `Suspended/` — Suspense + delay (깜빡임 방지)

---

## 8. shared/utils (순수 함수)

- `date.ts` — `formatDate`, `relativeTime` (date-fns 사용)
- `format.ts` — `formatNumber`, `truncate`
- `array.ts` — `groupBy`, `chunk`
- `object.ts` — `pick`, `omit`
- `string.ts` — `capitalize`, `slugify`

> lodash 전체 import는 회피. 필요한 함수만 직접 구현 또는 `lodash-es` 트리쉐이킹.

---

## 9. shared/i18n

```ts
// shared/i18n/ko.ts
export const ko = {
  common: {
    save: '저장',
    cancel: '취소',
    delete: '삭제',
  },
  session: {
    list: {
      empty: { title: '첫 모의면접을 시작해보세요', cta: '면접 시작' },
    },
  },
};
```

Phase 1은 한국어 only. Phase 2 영어 추가 대비해서 키 기반 운영.

---

## 10. shared/config

```ts
// shared/config/env.ts
export const env = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  SSE_BASE_URL: import.meta.env.VITE_SSE_BASE_URL ?? 'http://localhost:8080',
  GITHUB_OAUTH_CLIENT_ID: import.meta.env.VITE_GITHUB_OAUTH_CLIENT_ID ?? '',
} as const;
```

환경변수 접근은 항상 이 wrapper를 통해. 직접 `import.meta.env.*` 사용 금지 (테스트 어렵고, 누락 검증 못함).
