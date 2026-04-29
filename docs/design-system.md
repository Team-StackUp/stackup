# 디자인 시스템

> StackUp 프론트엔드의 시각·행동 규약. Figma 토큰과 1:1로 동기화된다.
> 구현 위치: `frontend/src/app/styles/`, `frontend/src/shared/ui/`

---

## 1. 디자인 원칙

1. **신뢰감 우선** — 면접 도구이므로 가벼운 톤 지양. 진중하고 차분한 시각 언어.
2. **집중 환경 보호** — 면접 화면은 노이즈 최소화. 핵심 UI만 노출, 부가 정보는 hover/click으로 점진적 공개.
3. **상태 가시성** — 분석 진행, 세션 진행 등 비동기 상태는 항상 명시적으로 보여준다.
4. **접근성 (WCAG 2.1 AA)** — 키보드 only 조작 가능, 명도 대비 4.5:1 이상.
5. **모바일 웹뷰 대응** — 데스크탑 우선이지만 mobile breakpoint에서도 깨지지 않게.

---

## 2. 디자인 토큰

### 2.1 컬러

#### Brand
| 토큰 | Light | Dark | 용도 |
|------|-------|------|------|
| `--color-brand-primary` | `#2563EB` | `#3B82F6` | CTA, 활성 상태 |
| `--color-brand-primary-hover` | `#1D4ED8` | `#60A5FA` | hover |
| `--color-brand-primary-pressed` | `#1E40AF` | `#2563EB` | active |
| `--color-brand-secondary` | `#10B981` | `#34D399` | 성공·완료 |

#### Semantic
| 토큰 | Light | Dark | 용도 |
|------|-------|------|------|
| `--color-success` | `#10B981` | `#34D399` | 성공 메시지, ANALYZED 뱃지 |
| `--color-warning` | `#F59E0B` | `#FBBF24` | 경고, PROCESSING |
| `--color-danger`  | `#EF4444` | `#F87171` | 에러, FAILED, 삭제 |
| `--color-info`    | `#3B82F6` | `#60A5FA` | 정보 토스트 |

#### Neutral
| 토큰 | 16진 | 용도 |
|------|------|------|
| `--color-bg` | `#FFFFFF` / `#0B0F19` | 페이지 배경 |
| `--color-surface` | `#F9FAFB` / `#111827` | 카드, 모달 |
| `--color-surface-raised` | `#FFFFFF` / `#1F2937` | 모달, 팝오버 |
| `--color-border` | `#E5E7EB` / `#374151` | 보더 |
| `--color-text-primary` | `#111827` / `#F9FAFB` | 본문 |
| `--color-text-secondary` | `#6B7280` / `#9CA3AF` | 부가 정보 |
| `--color-text-disabled` | `#D1D5DB` / `#4B5563` | 비활성 |

#### 도메인 컬러 (직군 / 면접 유형)
| 토큰 | 색상 |
|------|------|
| `--color-job-frontend` | `#22D3EE` (cyan) |
| `--color-job-backend`  | `#A78BFA` (violet) |
| `--color-job-infra`    | `#FB7185` (rose) |
| `--color-job-dba`      | `#FACC15` (amber) |
| `--color-type-personality` | `#34D399` |
| `--color-type-technical`   | `#60A5FA` |
| `--color-type-live-coding` | `#F472B6` |
| `--color-type-integrated`  | `#A78BFA` |

### 2.2 타이포그래피

폰트 스택:
```css
--font-sans: 'Pretendard Variable', 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
--font-mono: 'JetBrains Mono', 'D2Coding', 'Menlo', monospace;
```

| 토큰 | 크기 / 라인높이 / 굵기 | 용도 |
|------|------------------------|------|
| `--type-display` | 48 / 56 / 700 | 랜딩 히어로 |
| `--type-h1` | 32 / 40 / 700 | 페이지 제목 |
| `--type-h2` | 24 / 32 / 700 | 섹션 제목 |
| `--type-h3` | 20 / 28 / 600 | 카드 제목 |
| `--type-h4` | 18 / 26 / 600 | 서브 제목 |
| `--type-body-lg` | 16 / 24 / 400 | 본문 (기본) |
| `--type-body-md` | 14 / 20 / 400 | 보조 본문 |
| `--type-body-sm` | 12 / 18 / 400 | 캡션, 메타 |
| `--type-code-md` | 14 / 20 / 400 (mono) | 코드 |

### 2.3 간격 (Spacing) — 4px grid

| 토큰 | 값 |
|------|----|
| `--space-1` | 4px |
| `--space-2` | 8px |
| `--space-3` | 12px |
| `--space-4` | 16px |
| `--space-5` | 20px |
| `--space-6` | 24px |
| `--space-8` | 32px |
| `--space-10` | 40px |
| `--space-12` | 48px |
| `--space-16` | 64px |

### 2.4 라디우스

| 토큰 | 값 | 용도 |
|------|----|------|
| `--radius-sm` | 4px | 뱃지 |
| `--radius-md` | 8px | 버튼, 인풋 |
| `--radius-lg` | 12px | 카드 |
| `--radius-xl` | 16px | 모달 |
| `--radius-pill` | 9999px | 칩, 아바타 |

### 2.5 그림자 (Elevation)

| 토큰 | 값 |
|------|----|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.06)` |
| `--shadow-md` | `0 4px 6px -1px rgba(0,0,0,0.10), 0 2px 4px -2px rgba(0,0,0,0.06)` |
| `--shadow-lg` | `0 10px 15px -3px rgba(0,0,0,0.10), 0 4px 6px -4px rgba(0,0,0,0.05)` |
| `--shadow-focus-ring` | `0 0 0 3px rgba(37,99,235,0.45)` |

### 2.6 모션

| 토큰 | 값 | 용도 |
|------|----|------|
| `--duration-fast` | 120ms | hover, focus |
| `--duration-normal` | 200ms | 기본 transition |
| `--duration-slow` | 320ms | 모달 enter/exit |
| `--easing-standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | 기본 |
| `--easing-decelerate` | `cubic-bezier(0, 0, 0.2, 1)` | enter |
| `--easing-accelerate` | `cubic-bezier(0.4, 0, 1, 1)` | exit |

> `prefers-reduced-motion` 사용자에게는 모든 transition을 0ms로 강제.

### 2.7 Breakpoint

| 토큰 | min-width | 용도 |
|------|-----------|------|
| `--bp-sm` | 640px | 모바일 가로 |
| `--bp-md` | 768px | 태블릿 |
| `--bp-lg` | 1024px | 데스크탑 |
| `--bp-xl` | 1280px | 대형 모니터 |

---

## 3. 컴포넌트 인벤토리

> 위치: `frontend/src/shared/ui/{ComponentName}/`. 각 컴포넌트는 `index.ts`로 export.

### Foundation
- `Button` — variant: `primary | secondary | ghost | danger`, size: `sm | md | lg`, state: `loading | disabled`
- `IconButton` — 아이콘 전용, aria-label 필수
- `Link` — 인라인/블록, 외부 링크는 자동으로 `target="_blank" rel="noopener"`
- `Input` — text/email/url, prefix·suffix slot, 에러 메시지 슬롯
- `Textarea` — auto-grow 옵션
- `Select` — 단일 선택 (네이티브 select 래핑)
- `Combobox` — 검색·자동완성 (Repository 등록 등)
- `Checkbox`, `Radio`, `Switch`
- `FileUploader` — Drag & Drop, 진행률, 파일 검증 메시지

### Display
- `Badge` — 상태 표시 (READY/IN_PROGRESS/COMPLETED/FAILED 색상 매핑)
- `Tag` / `Chip` — 기술 스택 등 다중 라벨
- `Avatar` — GitHub avatar
- `Progress` — 선형 / 원형
- `Skeleton` — 로딩 placeholder
- `EmptyState` — 빈 목록 안내 (아이콘 + 설명 + CTA)
- `Card` — `header / body / footer` slot

### Feedback
- `Toast` — 4종 (success/info/warning/error), 우상단 stack, 4초 자동 dismiss
- `Modal` — `sm / md / lg / fullscreen`, focus trap 필수
- `Drawer` — 우측 슬라이드, 세션 설정 등
- `Popover`, `Tooltip` — 키보드 접근 가능
- `ConfirmDialog` — 파괴적 액션 (삭제, 회원 탈퇴) 전용
- `Alert` — 페이지 내 inline 경고

### Navigation
- `TopNav` — 로고, 글로벌 액션, Avatar 드롭다운
- `SideNav` — Workspace 좌측 메뉴
- `Tabs` — `Underline | Pills`
- `Breadcrumb`
- `Pagination`

### Domain-aware (`shared/ui` 가 아닌 `features/*/ui`로 위치)
- `JobCategoryBadge` — 직군별 컬러
- `InterviewTypeBadge` — 면접 유형별 컬러
- `SessionStatusBadge`
- `AnalysisStateIndicator` — QUEUED/PROCESSING/COMPLETED/FAILED 4단계 progress

> **shared vs features 구분**: 도메인 의존성이 있으면 features로. 디자인 토큰만 사용하면 shared로.

---

## 4. 상태별 색상 매핑 (Status Color Map)

| 상태 | 색상 | 컴포넌트 예시 |
|------|------|----------------|
| `READY` / `PENDING` / `QUEUED` | neutral / `text-secondary` | 회색 Badge |
| `IN_PROGRESS` / `PROCESSING` / `ANALYZING` | warning | 노란 Badge + spinner |
| `COMPLETED` / `ANALYZED` / `ACTIVE` | success | 초록 Badge |
| `INTERRUPTED` | warning | 노란 Badge (느낌표) |
| `FAILED` | danger | 빨간 Badge + 재시도 버튼 |
| `CANCELLED` / `ARCHIVED` | text-disabled | 회색 strikethrough |

**구현 컨벤션**: `frontend/src/shared/ui/StatusBadge` 단일 컴포넌트가 모든 매핑을 캡슐화. 새 상태 추가 시 이 컴포넌트만 수정.

---

## 5. 페이지 레이아웃 그리드

```
┌────────────────────────────────────────────────────┐
│ TopNav (height: 64px, sticky)                      │
├──────────┬─────────────────────────────────────────┤
│          │                                         │
│ SideNav  │  Main Content                           │
│ (240px)  │  - max-width: 1280px                    │
│          │  - padding: 24px (mobile: 16px)         │
│          │                                         │
└──────────┴─────────────────────────────────────────┘
```

특수 페이지:
- **Login**: SideNav 없음, 중앙 정렬 카드
- **Interview**: SideNav 숨김(focus mode), 좌측 면접관, 우측 답변 영역, 하단 컨트롤
- **Feedback Report**: TopNav만, 인쇄 친화적 레이아웃 (`@media print`)

---

## 6. 다크 모드

- `<html data-theme="light|dark">` 로 토글
- `prefers-color-scheme` 자동 감지 + 사용자 명시 선택 우선
- 토큰만 갱신 (컴포넌트 코드 수정 불필요)
- 면접 진행 중에는 다크 모드 강제 (눈 피로 감소) — Phase 2에서 검토

---

## 7. 아이콘

- 라이브러리: **Lucide Icons** (트리쉐이킹 지원)
- 크기: 16/20/24px (line height와 정렬)
- 색상: `currentColor` (텍스트 색상 상속)
- 의미 있는 아이콘은 `aria-label` 필수, 장식용은 `aria-hidden="true"`

---

## 8. 토큰 → 코드 매핑

`frontend/src/app/styles/tokens.css`:
```css
:root {
  /* 위 표의 모든 토큰을 CSS 변수로 정의 */
  --color-brand-primary: #2563EB;
  --space-4: 16px;
  /* ... */
}

[data-theme='dark'] {
  --color-brand-primary: #3B82F6;
  /* ... 다크모드 오버라이드 */
}
```

컴포넌트는 토큰만 참조:
```tsx
// 좋음
<button className="bg-[var(--color-brand-primary)]">

// 나쁨 (하드코딩)
<button className="bg-blue-500">
```

> Tailwind를 쓰는 경우 `tailwind.config.js`의 `theme.extend.colors`를 토큰과 매핑한다.

---

## 9. 접근성 체크리스트 (모든 컴포넌트)

- [ ] 키보드만으로 모든 액션 가능
- [ ] focus-visible 스타일 명확 (`--shadow-focus-ring`)
- [ ] semantic HTML 사용 (`<button>` 대신 `<div onClick>` 금지)
- [ ] aria-label / aria-describedby 명시 (의미 전달)
- [ ] 명도 대비 4.5:1 이상 (텍스트 ≥ 14px), 3:1 이상 (큰 텍스트)
- [ ] form은 `<label htmlFor>` 또는 `aria-label`
- [ ] 동적 콘텐츠는 `aria-live` 영역
- [ ] reduced-motion 대응

---

## 10. 컴포넌트 추가 절차

1. Figma에서 디자인 확정 → 토큰 사용 여부 확인
2. `frontend/src/shared/ui/{Name}/` 디렉토리 생성
   - `{Name}.tsx`, `{Name}.test.tsx`, `{Name}.stories.tsx`(옵션), `index.ts`
3. props는 minimal하게. variant는 union string.
4. 본 문서 §3 인벤토리에 등록
5. PR 시 스크린샷 첨부 (light/dark 양쪽)
