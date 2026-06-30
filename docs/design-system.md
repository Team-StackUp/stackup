# 디자인 시스템

> StackUp 프론트엔드 시각·행동 규약. **Tailwind CSS v4 기반 단일 출처(SSOT).**
> 구현 — [`frontend/src/app/styles/`](../frontend/src/app/styles/) (`tokens.css`, `global.css`, `index.css`)
> 데모 페이지 — [`frontend/src/App.tsx`](../frontend/src/App.tsx) (`npm run dev` → `http://localhost:5173`)
> 컴포넌트 — [`frontend/src/shared/ui/`](../frontend/src/shared/ui/) (도메인 비종속), [`frontend/src/features/*/ui/`](../frontend/src/features/) (도메인 종속)

---

## 1. 디자인 원칙

1. **신뢰감 우선** — 면접 도구이므로 가벼운 톤 지양. 진중·차분한 시각 언어.
2. **집중 환경 보호** — 면접 화면은 노이즈 최소화. 핵심 UI만 노출, 부가 정보는 hover/click 으로 점진적 공개.
3. **상태 가시성** — 분석 진행, 세션 상태, SSE 이벤트 등 비동기 상태는 항상 명시적으로 표현.
4. **모노크로매틱 + 의미 컬러** — 로고 블루(#3E63D8) 기반 **쿨 블루그레이** 단일 베이스(토큰명은 `sage-*` 유지)로 톤 일관성을 유지하고, 브랜드 강조는 로고 블루 `primary`, Status / Domain 만 muted jewel tone 으로 식별성 부여.
5. **접근성 (WCAG 2.1 AA)** — 키보드 only 조작, 명도 대비 4.5:1 이상, focus ring 명확.
6. **모바일 웹뷰 대응** — 데스크탑 우선이지만 mobile breakpoint(`< lg`)에서도 깨지지 않게.

---

## 2. 디자인 토큰 — 단일 출처

모든 토큰은 [`frontend/src/app/styles/tokens.css`](../frontend/src/app/styles/tokens.css) 의 `@theme` 블록에만 정의. 컴포넌트는 토큰만 참조하고 **하드코딩 금지** (`bg-[#626e5c]` ✗ → `bg-primary` ✓).

### 2.1 컬러 — Base

| 토큰 | Hex | Tailwind | 용도 |
|---|---|---|---|
| `--color-white` | `#ffffff` | `bg-white`, `text-white` | 순백 |
| `--color-black` | `#000000` | `bg-black` | 순흑 |
| `--color-background` | `#eceef3` | `bg-background` (= `bg-bg`) | 앱 기본 배경 (쿨 라이트) |

### 2.2 컬러 — Sage Scale (메인 테마, 11 단계)

> 토큰 이름은 `sage-*` 로 유지하되 **값은 로고(#3E63D8)에 맞춘 쿨 블루그레이 모노크롬** 이다(저채도 — 중립/본문용). 브랜드 강조색은 별도 `primary` (아래 §2.3, 스케일과 분리).

| 토큰 | Hex | 권장 용도 |
|---|---|---|
| `sage-50` | `#eef1f6` | 가장 밝은 컴포넌트 배경 (= `surface`) |
| `sage-100` | `#dbe2ec` | 분리선 / 보더 (= `border`) |
| `sage-200` | `#c6cedd` | 비활성 텍스트 / 보조 배경 (= `border-strong`, `fg-disabled`) |
| `sage-300` | `#9fafc9` | 비활성 보조 / placeholder |
| `sage-400` | `#6e7f9f` | 흐린 텍스트 (= `fg-subtle`) |
| `sage-500` | `#4a5a7e` | 본문 보조 텍스트 (= `fg-muted`), 활성 / 포커스 |
| `sage-600` | `#38486a` | 강조 보더 / 표면 |
| `sage-700` | `#2b3a59` | 강조 컴포넌트 |
| `sage-800` | `#1e2a44` | 주요 헤딩 · 푸터 네이비 (= `fg-strong`) |
| `sage-900` | `#161f33` | 본문 (대안) |
| `sage-950` | `#101627` | 가장 어두운 텍스트 (= `fg`, 기본) |

Tailwind 사용: `bg-sage-{n}`, `text-sage-{n}`, `border-sage-{n}`.

### 2.3 컬러 — Semantic Aliases

`sage-*` 직접 참조보다 의미 별칭 사용을 권장 (다크 모드 도입 시 자동 대응).

| 토큰 | 매핑 | Tailwind |
|---|---|---|
| `--color-bg` | `background` | `bg-bg` |
| `--color-surface` | `sage-50` | `bg-surface` |
| `--color-surface-raised` | `white` | `bg-surface-raised` |
| `--color-border` | `sage-100` | `border-border` |
| `--color-border-strong` | `sage-200` | `border-border-strong` |
| `--color-fg` | `sage-950` | `text-fg` |
| `--color-fg-strong` | `sage-800` | `text-fg-strong` |
| `--color-fg-muted` | `sage-500` | `text-fg-muted` |
| `--color-fg-subtle` | `sage-400` | `text-fg-subtle` |
| `--color-fg-disabled` | `sage-200` | `text-fg-disabled` |
| `--color-fg-on-primary` | `white` | `text-fg-on-primary` |
| `--color-primary` | `#3e63d8` (로고 블루, 스케일과 분리) | `bg-primary`, `text-primary` |
| `--color-primary-hover` | `#3050be` | `bg-primary-hover` |
| `--color-primary-pressed` | `#274099` | `bg-primary-pressed` |

### 2.4 컬러 — Status

상태 시각화. Sage 톤과 조화되는 muted jewel tones.

| 카테고리 | -50 (연한 배경) | -500 (기본) | -700 (강조) | 단축 |
|---|---|---|---|---|
| Success | `#e8efe1` | `#5b7c47` | `#3f5731` | `bg-success`, `text-success` |
| Warning | `#f4e8d4` | `#b88840` | `#8a6529` | `bg-warning`, `text-warning` |
| Danger  | `#f4e0d8` | `#a8503c` | `#803a2a` | `bg-danger`, `text-danger` |
| Info    | `#dde4ea` | `#4d6878` | `#36475a` | `bg-info`, `text-info` |

**조합 규칙**:
- 솔리드 뱃지: `bg-success text-white`
- 연한 뱃지: `bg-success-50 text-success-700`
- 인라인 텍스트 강조: `text-success-700`

### 2.5 컬러 — Domain (Jobs / Interview Types)

StackUp 도메인 한정 컬러. `features/*/ui` 의 도메인 뱃지에서만 사용 (`shared/ui` 에서 직접 참조 금지).

#### 직군

| 직군 | Hex | Tailwind |
|---|---|---|
| Frontend | `#5e8a98` (teal) | `bg-job-frontend` |
| Backend  | `#7d6c93` (plum) | `bg-job-backend` |
| Infra    | `#b06c70` (rose) | `bg-job-infra` |
| DBA      | `#b89c5e` (gold) | `bg-job-dba` |

#### 면접 모드

| 모드 | Hex | Tailwind |
|---|---|---|
| Personality | `#6f9978` (mint)  | `bg-type-personality` |
| Technical   | `#6c8294` (slate) | `bg-type-technical` |
| Integrated  | `#8a7896` (violet)| `bg-type-integrated` |

### 2.6 타이포그래피 — 폰트 패밀리 (3-Tier)

| 토큰 | 폰트 | 용도 | Tailwind |
|---|---|---|---|
| `--font-heading` / `--font-display` | Fira Sans Extra Condensed | H1~H3 (Uppercase, 임팩트) | `font-heading`, `font-display` |
| `--font-subheading` | Geist | H4~H6, Caption (모던) | `font-subheading` |
| `--font-sans` / `--font-body` | Inter | 본문, 버튼 (가독성) | `font-sans`, `font-body` |
| `--font-mono` | Geist Mono | 코드 / 숫자 모노 | `font-mono` |

폰트 로딩은 [`frontend/index.html`](../frontend/index.html) 의 `<link>` (Google Fonts).

### 2.7 타이포그래피 — 텍스트 스케일

각 토큰은 `font-size + line-height + letter-spacing + font-weight` 페어. 반응형은 `*-mobile` 변형.

| Tailwind | Size / LH | Weight | 폰트 카테고리 | 용도 |
|---|---|---|---|---|
| `text-display` (`text-display-mobile`) | 100/1.0 (48/1.05) | 700 | Heading | 랜딩 / 페이지 타이틀 |
| `text-h2` (`text-h2-mobile`) | 56/1.05 (42/1.1) | 700 | Heading | 섹션 제목 |
| `text-h3` | 38/1.1 | 700 | Heading | 카드 / 블록 제목 |
| `text-h4` | 32/1.2 | 700 | Subheading | 큰 서브 헤딩 |
| `text-h5` | 24/1.3 | 700 | Subheading | 중간 서브 헤딩 |
| `text-h6` | 20/1.4 | 700 | Subheading | 작은 서브 헤딩 |
| `text-rich` | 20/1.5 | 500 | Subheading | 큰 본문 / 강조 |
| `text-body` | 16/1.2 | 500 | Sans | 기본 본문 |
| `text-button` | 14/1.2 | 600 | Sans | 버튼 / 작은 본문 |
| `text-caption` | 12/1.4 | 400 | Subheading | 캡션 / 메타 |

> **반응형 헤딩 사용법**: `<h1 className="text-display-mobile lg:text-display">`

> **계층 가이드**: 한 페이지에 `text-display` 1개, `text-h2`/`text-h3` 는 섹션 단위. 카드 안 헤더는 `text-h5`/`text-h6` 우선.

### 2.8 간격 (Spacing — 4px Grid)

Tailwind v4 기본 `--spacing: 0.25rem` (= 4px) 사용. `p-4` = `16px`.

| 토큰 | 값 |
|---|---|
| `space-1` | 4px |
| `space-2` | 8px |
| `space-3` | 12px |
| `space-4` | 16px |
| `space-5` | 20px |
| `space-6` | 24px |
| `space-8` | 32px |
| `space-10` | 40px |
| `space-12` | 48px |
| `space-16` | 64px |

권장 — 위 10개 값 우선. 임의 값(`p-[13px]`)은 디자인 의도가 분명할 때만.

### 2.9 라디우스

| 토큰 | 값 | Tailwind | 용도 |
|---|---|---|---|
| `--radius-sm` | 4px | `rounded-sm` | 뱃지 |
| `--radius-md` | 8px | `rounded-md` | 버튼, 인풋 |
| `--radius-lg` | 12px | `rounded-lg` | 카드 |
| `--radius-xl` | 16px | `rounded-xl` | 모달 |
| `--radius-2xl` | 24px | `rounded-2xl` | 큰 모달 / Hero |
| `--radius-pill` | 9999px | `rounded-pill` | 칩, 아바타, 토글 |

### 2.10 그림자 / Elevation (Sage-tint)

| 토큰 | Tailwind | 값 |
|---|---|---|
| `--shadow-sm` | `shadow-sm` | `0 1px 2px rgba(31,39,27,.06)` |
| `--shadow-md` | `shadow-md` | `0 4px 6px -1px rgba(31,39,27,.10), 0 2px 4px -2px rgba(31,39,27,.06)` |
| `--shadow-lg` | `shadow-lg` | `0 10px 15px -3px rgba(31,39,27,.10), 0 4px 6px -4px rgba(31,39,27,.05)` |
| `--shadow-focus-ring` | `shadow-focus-ring` | `0 0 0 3px rgba(98,110,92,.45)` |

### 2.11 모션

| 토큰 | 값 | Tailwind | 용도 |
|---|---|---|---|
| `--duration-fast` | 120ms | `duration-fast` | hover, focus |
| `--duration-normal` | 200ms | `duration-normal` | 기본 transition |
| `--duration-slow` | 320ms | `duration-slow` | 모달 enter / exit |
| `--ease-standard` | `cubic-bezier(0.4,0,0.2,1)` | `ease-standard` | 기본 |
| `--ease-decelerate` | `cubic-bezier(0,0,0.2,1)` | `ease-decelerate` | enter |
| `--ease-accelerate` | `cubic-bezier(0.4,0,1,1)` | `ease-accelerate` | exit |

> `prefers-reduced-motion: reduce` 사용자에게는 모든 transition / animation 0.01ms 강제 (`global.css` 에서 처리).

### 2.12 Z-Index

레이어 충돌 방지용 정수 스케일. Tailwind 자동 utility 미생성 (`--z-*` 는 namespace 가 아님) → CSS var 직접 사용.

| 토큰 | 값 | 용도 |
|---|---|---|
| `--z-base` | 0 | 기본 |
| `--z-raised` | 10 | 카드 hover, 떠오름 |
| `--z-dropdown` | 1000 | Select, Menu |
| `--z-sticky` | 1100 | Sticky TopNav |
| `--z-modal-backdrop` | 1200 | 모달 dim |
| `--z-modal` | 1300 | 모달 본체 |
| `--z-popover` | 1400 | Popover |
| `--z-tooltip` | 1500 | Tooltip |
| `--z-toast` | 1600 | Toast (가장 위) |

```tsx
<div className="z-[var(--z-modal)]">...</div>
// or
<div style={{ zIndex: 'var(--z-modal)' }}>...</div>
```

### 2.13 컨테이너 / Breakpoint

| 토큰 | 값 | Tailwind | 용도 |
|---|---|---|---|
| `--container-readable` | 65ch | `max-w-readable` | 본문 / 프로즈 카드 |
| `--container-content` | 1280px | `max-w-content` | TopNav 안쪽 메인 영역 |
| `--container-app` | 1440px | `max-w-app` | 앱 전체 최대 폭 |

**Breakpoints** — Tailwind v4 default 사용:
- `sm: 640px`, `md: 768px`, `lg: 1024px`, `xl: 1280px`, `2xl: 1536px`.

### 2.14 아이콘

- 라이브러리 — **Lucide Icons** (`lucide-react` 도입 예정, 트리쉐이킹 지원).
- 크기 — `16 / 20 / 24 px` (line-height 와 정렬).
- 색상 — `currentColor` (텍스트 색 상속). Tailwind: `text-fg`, `text-fg-muted` 적용.
- 의미 있는 아이콘은 `aria-label` 필수, 장식용은 `aria-hidden="true"`.

---

## 3. 컴포넌트 인벤토리

위치: [`frontend/src/shared/ui/{Name}/`](../frontend/src/shared/ui/) (도메인 비종속) 또는 [`frontend/src/features/*/ui/`](../frontend/src/features/) (도메인 종속). 각 컴포넌트는 `index.ts` 로 public API.

### Foundation
- `Button` — variant: `primary | secondary | ghost | danger` · size: `sm | md | lg` · state: `loading | disabled`.
- `IconButton` — 아이콘 전용, `aria-label` 필수.
- `Link` — 인라인 / 블록, 외부 링크는 자동 `target="_blank" rel="noopener"`.
- `Input`, `Textarea`, `Select`, `Combobox` (검색·자동완성).
- `Checkbox`, `Radio`, `Switch`.
- `FileUploader` — Drag & Drop, 진행률, 파일 검증 메시지.

### Display
- `Badge` — 상태 매핑 (§4 참조).
- `Tag` / `Chip` — 기술 스택 등 다중 라벨.
- `Avatar` — GitHub avatar.
- `Progress` — 선형 / 원형.
- `Skeleton` — 로딩 placeholder (4-state §5).
- `EmptyState` — 빈 목록 안내, 아이콘 + 설명 + CTA (4-state §5).
- `Card` — `header / body / footer` slot.

### Feedback
- `Toast` — 4종 (success / info / warning / error), 우상단 stack, 4초 자동 dismiss, `z-toast`.
- `Modal` ✅ — `shared/ui/Modal`. `title` + `children` + 옵션 `footer` 슬롯, Esc·백드롭 클릭으로 닫힘, `z-modal`, body 스크롤 잠금 + 포커스 복원. (전체 focus trap 은 추후)
- `Drawer` — 우측 슬라이드, 세션 설정 등.
- `Popover`, `Tooltip` — 키보드 접근 가능.
- `ConfirmDialog` — 파괴적 액션(삭제, 회원 탈퇴) 전용.
- `Alert` — 페이지 inline 경고.

### Navigation
- `TopNav` — 로고, 글로벌 액션, Avatar 드롭다운, 64px height, sticky, `z-sticky`.
- `SideNav` — Workspace 좌측 메뉴, 240px width.
- `Tabs` — `Underline | Pills`.
- `Breadcrumb`, `Pagination`.

### Domain-aware (`features/*/ui` 에 위치)
- `JobCategoryBadge` — 직군별 컬러 (§2.5).
- `InterviewModeBadge` — 면접 모드별 컬러 (§2.5).
- `SessionStatusBadge` — 상태 매핑 (§4).
- `AnalysisStateIndicator` — `QUEUED / PROCESSING / COMPLETED / FAILED` 4단계 progress.

> **shared vs features 구분** — 도메인 의존이 있으면 `features`, 디자인 토큰만 참조하면 `shared`.

---

## 4. 상태 ↔ 컬러 매핑

| 도메인 상태 | 시각 컬러 | 토큰 | 컴포넌트 예 |
|---|---|---|---|
| `READY` / `PENDING` / `QUEUED` | neutral | `text-fg-muted` (sage-500) | 회색 Badge |
| `IN_PROGRESS` / `PROCESSING` / `ANALYZING` | warning | `bg-warning-50 text-warning-700` | 노란 Badge + spinner |
| `COMPLETED` / `ANALYZED` / `ACTIVE` | success | `bg-success-50 text-success-700` | 초록 Badge |
| `INTERRUPTED` | warning | `bg-warning-50 text-warning-700` | 노란 Badge (느낌표 아이콘) |
| `FAILED` | danger | `bg-danger-50 text-danger-700` | 빨간 Badge + 재시도 |
| `CANCELLED` / `ARCHIVED` | disabled | `text-fg-disabled` | 회색 strikethrough |

**구현 컨벤션** — `frontend/src/shared/ui/StatusBadge` 단일 컴포넌트가 모든 매핑을 캡슐화. 새 상태 추가 시 이 컴포넌트만 수정.

---

## 5. 4-State UI 패턴 (비동기)

서버 데이터의 4가지 상태별 UI 분기. [`frontend/src/shared/lib/AsyncBoundary`](../frontend/src/shared/lib/AsyncBoundary/) 로 일원화.

| 상태 | UI | 컴포넌트 |
|---|---|---|
| **Loading (Pending)** | shape-mimicking placeholder | `Skeleton` |
| **Empty** | 아이콘 + 설명 + CTA | `EmptyState` |
| **Error (Rejected)** | 메시지 + 재시도 버튼 | `Alert` (또는 `rejectedFallback`) |
| **Success** | 정상 컨텐츠 | (각 페이지 자체) |

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

상세: [`frontend/src/shared/CLAUDE.md`](../frontend/src/shared/CLAUDE.md), [`docs/ui-patterns.md`](./ui-patterns.md).

---

## 6. 페이지 레이아웃

```
┌────────────────────────────────────────────────────┐
│ TopNav (height: 64px, sticky, z-sticky)            │
├──────────┬─────────────────────────────────────────┤
│          │                                         │
│ SideNav  │ Main Content                            │
│ (240px)  │  - max-w-content (1280px)               │
│          │  - px-6 lg:px-12, py-12                 │
│          │                                         │
└──────────┴─────────────────────────────────────────┘
```

특수 페이지:
- **Login** — SideNav 없음, 중앙 정렬 카드.
- **Interview** — focus mode (TopNav 숨김 옵션), 좌측 면접관, 우측 답변 영역, 하단 컨트롤.
- **Feedback Report** — TopNav 만, 인쇄 친화 (`@media print`).

---

## 7. 다크 모드 (Phase 2 예정)

- `<html data-theme="light|dark">` 토글.
- `prefers-color-scheme` 자동 감지 + 사용자 명시 선택 우선.
- **토큰만 갱신** (컴포넌트 코드 수정 X) — `[data-theme='dark'] { --color-bg: ...; ... }`.
- 면접 진행 중 다크 모드 강제 옵션 검토 (눈 피로 감소).

다크 토큰 정의는 Phase 2 진입 시 [`frontend/src/app/styles/tokens.css`](../frontend/src/app/styles/tokens.css) 에 추가.

---

## 8. 토큰 → Tailwind 매핑 빠른 참조

| CSS Variable | Tailwind Utility |
|---|---|
| `--color-sage-{50..950}` | `bg-sage-{n}`, `text-sage-{n}`, `border-sage-{n}` |
| `--color-primary` / `--color-fg` / `--color-bg` | `bg-primary` / `text-fg` / `bg-bg` |
| `--color-success` / `--color-success-50` / `--color-success-700` | `bg-success` / `bg-success-50` / `text-success-700` |
| `--color-job-frontend` | `bg-job-frontend`, `text-job-frontend` |
| `--font-heading` / `--font-sans` / `--font-mono` | `font-heading` / `font-sans` / `font-mono` |
| `--text-display` (+ paired LH / letter / weight) | `text-display` |
| `--radius-md` / `--radius-pill` | `rounded-md` / `rounded-pill` |
| `--shadow-md` / `--shadow-focus-ring` | `shadow-md` / `shadow-focus-ring` |
| `--duration-normal` / `--ease-standard` | `duration-normal` / `ease-standard` |
| `--container-content` / `--container-readable` | `max-w-content` / `max-w-readable` |
| `--z-modal` / `--z-toast` | `z-[var(--z-modal)]` (namespace 미생성) |

**원칙** — 컴포넌트는 utility class 만 사용. `bg-[#626e5c]` 같은 직접 hex 사용 금지.

---

## 9. 접근성 체크리스트 (모든 컴포넌트)

- [ ] 키보드만으로 모든 액션 가능 (`tab`, `enter`, `esc`, 화살표).
- [ ] focus-visible 명확 (`outline: 2px solid var(--color-primary)` — `global.css` default).
- [ ] semantic HTML (`<button>` 대신 `<div onClick>` 금지, `<a>` vs `<button>` 구분).
- [ ] `aria-label` / `aria-describedby` 명시 (의미 전달 필요 시).
- [ ] 명도 대비 — 텍스트 4.5:1, 큰 텍스트 (≥ 18.66px or ≥ 14px bold) 3:1.
- [ ] form 은 `<label htmlFor>` 또는 `aria-label` 필수.
- [ ] 동적 콘텐츠는 `aria-live="polite"` 영역.
- [ ] `prefers-reduced-motion` 대응 (전역 — `global.css`).
- [ ] 모달 / Drawer 는 focus trap + `esc` 닫기.

---

## 10. 컴포넌트 추가 절차

1. **Figma 디자인 확정** → 토큰만 사용하는지 검증 (하드코딩 X).
2. **디렉토리 생성** — `frontend/src/shared/ui/{Name}/`
   - `{Name}.tsx`, `{Name}.test.tsx`, `{Name}.stories.tsx`(옵션), `index.ts`.
3. **Props minimal** — variant 는 union string (`"primary" | "secondary"`).
4. **본 문서 §3 인벤토리 등록**.
5. **PR 시 스크린샷 첨부** (light, 향후 dark 도입 시 양쪽).

---

## 11. 자주 묻는 작업

| 작업 | 위치 |
|---|---|
| 토큰 추가 / 변경 | [`frontend/src/app/styles/tokens.css`](../frontend/src/app/styles/tokens.css) + 본 문서 §2 |
| 글로벌 스타일 (reset, base) | [`frontend/src/app/styles/global.css`](../frontend/src/app/styles/global.css) |
| 신규 공통 컴포넌트 | `frontend/src/shared/ui/{Name}/` |
| 도메인 컴포넌트 | `frontend/src/features/{domain}/ui/` |
| 데모 페이지 확인 | `npm run dev` (frontend) → `http://localhost:5173` |
| 폰트 추가 | [`frontend/index.html`](../frontend/index.html) `<link>` + `tokens.css` `--font-*` |

---

## 12. 변경 이력

- **2026-06** — **블루 리테마**: 로고(#3E63D8)에 맞춰 `sage-*` 스케일 값을 쿨 블루그레이 모노크롬으로 교체(토큰명 유지), `primary` 를 스케일에서 분리해 로고 블루로 지정. 배경/그림자/포커스링도 쿨톤·네이비로 재조정. 위젯 하드코딩 hex 일괄 치환. Status / Domain 컬러는 유지.
- **2026-05** — Tailwind CSS v4 기반 **sage 모노크로매틱** 시스템으로 전면 개편. 이전 `Pretendard + 블루 브랜드` 스펙은 본 문서로 흡수.
- **2026-05** — Status / Domain 컬러를 muted jewel tone 으로 재정의 (Sage 톤과 조화).
- **2026-05** — Z-index, Container max-width, 4-State UI 패턴 등 누락 토큰 / 규약 보강.
