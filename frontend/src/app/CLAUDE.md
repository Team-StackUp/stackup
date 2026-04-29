# `app/` — 앱 부트스트랩 레이어

> FSD에서 가장 상위 레이어. 앱 전체에 영향을 주는 설정만 둔다. 비즈니스 로직 금지.

상위: [`../CLAUDE.md`](../CLAUDE.md)

---

## 1. 책임

| 디렉토리 | 책임 |
|----------|------|
| `providers/` | React 프로바이더 합성 (QueryClient, Router, Theme, Auth, ErrorBoundary 등) |
| `router/` | 라우트 정의, lazy import, layout |
| `styles/` | 디자인 토큰 CSS, 글로벌 reset, 폰트 |

**비책임**: 페이지 구현, 도메인 로직, API 호출.

---

## 2. providers 구성

`AppProviders`로 한 곳에 합성:

```tsx
// app/providers/AppProviders.tsx
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider>
          <RouterProvider router={router} />
          <ToastViewport />
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
```

순서 원칙 (안에서부터 바깥):
1. Router (가장 안쪽 — 페이지가 다른 provider를 사용하므로 외곽이 먼저 설정되어야 함)
2. Auth (페이지가 의존)
3. Theme
4. QueryClient (server state — 가장 바깥, 전역 캐시)
5. (옵션) Sentry, GA 등 모니터링

`main.tsx`:
```tsx
ReactDOM.createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders />
  </StrictMode>
);
```

---

## 3. router 구성

- 라우트는 객체로 선언 (코드 분할 위해 `lazy` import)
- 각 페이지의 `index.ts`에서 default export

```tsx
// app/router/routes.tsx
export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, lazy: () => import('@/pages/Login') },
      { path: 'workspace', lazy: () => import('@/pages/Workspace') },
      { path: 'sessions/:id', lazy: () => import('@/pages/Interview') },
      // ...
    ],
  },
]);
```

레이아웃:
- `RootLayout` — TopNav + Outlet
- `WorkspaceLayout` — TopNav + SideNav + Outlet
- `FocusLayout` — 면접 진행 시 minimal (TopNav 숨김)

---

## 4. styles 구성

```
app/styles/
├── tokens.css        # CSS variables (디자인 토큰)
├── reset.css         # CSS reset (modern-normalize 권장)
├── global.css        # 폰트, html/body 기본
└── index.ts          # import 진입점
```

`main.tsx`에서 `import '@/app/styles'`로 일괄 로드.

토큰 정의는 [`/docs/design-system.md §2`](../../../docs/design-system.md) 참조.

---

## 5. 절대 하지 말 것

- 비즈니스 컴포넌트(`features/*`, `domain/*`) 직접 import해서 여기서 조립 ✗
  → providers의 wrap 역할로 한정
- API 호출 ✗
- DOM 조작 (`document.querySelector` 등) ✗
- 라우트 외 navigation 로직 ✗
