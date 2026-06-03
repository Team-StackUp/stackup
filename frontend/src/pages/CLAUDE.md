# `pages/` — 페이지 레이어

> 라우트 1개 = 페이지 1개. **composition만** 담당. 로직은 `features` 또는 `domain`으로.

상위: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 1. 책임

- URL 파라미터 / 쿼리스트링 추출
- 레이아웃 조립 (어떤 feature를 어디 배치할지)
- 페이지 메타 (title, og:image)
- 페이지 단위 에러/로딩 boundary

**비책임**: 데이터 fetch, 상태 관리, 비즈니스 규칙.

---

## 2. 슬라이스 구조

```
pages/Workspace/
├── ui/
│   └── WorkspacePage.tsx   # 페이지 컴포넌트 (default export)
├── model/                  # 페이지 단위 상태 (탭 active 등 minor)
└── index.ts                # default export WorkspacePage
```

- `index.ts`는 default export만 (router의 `lazy()`가 `{ default }` 기대)
- 페이지가 매우 단순하면 `ui/` 생략하고 `index.tsx`에 직접 작성 OK

---

## 3. 페이지 인벤토리 (Phase 1)

| 페이지 | 경로 | 주요 features |
|--------|------|----------------|
| `Login` | `/login` | `features/auth` |
| `Workspace` | `/workspace` | `features/resume`, `features/repo` (도입 예정) |
| `Interview` | `/sessions/new`, `/sessions/:id` | `features/interview` |
| `Interview (Feedback)` | `/sessions/:id/feedback` | `features/feedback` |
| `History` | `/workspace/history` (구 `/history` → 리다이렉트) | `features/history` |

---

## 4. 데이터 prefetch

페이지 진입 시 필요한 데이터는 라우터 loader 또는 page-level Suspense로:

```tsx
// pages/Interview/ui/InterviewPage.tsx
export function InterviewPage() {
  const { id } = useParams();
  return (
    <FocusLayout>
      <AsyncBoundary
        pendingFallback={<InterviewSkeleton />}
        rejectedFallback={({ error, reset }) => <InterviewError error={error} onRetry={reset} />}
      >
        <InterviewSessionView sessionId={Number(id)} />
      </AsyncBoundary>
    </FocusLayout>
  );
}

export default InterviewPage;
```

`InterviewSessionView`는 `features/interview` 가 제공.

---

## 5. 페이지 간 이동

- 라우터 navigate (`useNavigate`)만 사용
- `window.location` 직접 조작 ✗
- 다른 페이지 컴포넌트 직접 import ✗ (오직 라우터 lazy import)

---

## 6. SEO·메타

- `<title>` 페이지별 갱신: 페이지 진입 시 `useEffect`로 `document.title` 또는 라이브러리(react-helmet-async)
- og:image, description 등은 페이지 메타 객체로 통합 관리 (도입 시 추가)

---

## 7. 페이지 단위 권한

라우트 보호는 `app/router`의 layout level에서 처리. 페이지 안에서 다시 권한 체크 X.

```tsx
// app/router에서
{
  path: 'workspace',
  element: <RequireAuth><WorkspaceLayout /></RequireAuth>,
  children: [...],
}
```
