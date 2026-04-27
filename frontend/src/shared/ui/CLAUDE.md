# `shared/ui/` — 디자인 시스템 컴포넌트

> 도메인 비종속 재사용 컴포넌트. 디자인 토큰만 의존. 인벤토리/토큰 정의는 [`/docs/design-system.md`](../../../../docs/design-system.md) 참조.

상위: [`../CLAUDE.md`](../CLAUDE.md)

---

## 1. 한 컴포넌트의 표준 구조

```
shared/ui/{Component}/
├── {Component}.tsx        # 컴포넌트 구현
├── {Component}.types.ts   # props 타입 (간단하면 .tsx에 inline)
├── {Component}.test.tsx   # 단위 테스트
├── {Component}.stories.tsx # (옵션) Storybook
├── {Component}.module.css # (옵션) CSS Modules
└── index.ts               # named export
```

`index.ts`:
```ts
export { Button } from './Button';
export type { ButtonProps } from './Button.types';
```

---

## 2. props 설계 원칙

### variant 패턴 (union string)
```ts
type ButtonProps = {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  disabled?: boolean;
  // ...
} & ButtonHTMLAttributes<HTMLButtonElement>;
```

- variant는 **3~5개 이내** 권장. 더 많아지면 다른 컴포넌트로 분할.
- HTML 속성은 `extends` 또는 intersection으로 흡수.

### Composition over configuration
```tsx
// 좋음 (composition)
<Card>
  <Card.Header>제목</Card.Header>
  <Card.Body>내용</Card.Body>
</Card>

// 나쁨 (props bloat)
<Card title="제목" body="내용" footer="..." />
```

### 제어/비제어
form 요소는 양쪽 지원:
```ts
type InputProps = {
  value?: string;          // 제어
  defaultValue?: string;   // 비제어
  onChange?: (v: string) => void;
};
```

---

## 3. 토큰만 사용

```tsx
// 좋음
<button style={{ background: 'var(--color-brand-primary)' }}>

// 나쁨
<button style={{ background: '#2563EB' }}>
```

CSS Modules 또는 Tailwind 사용 시도 동일. 토큰은 `app/styles/tokens.css`.

---

## 4. 접근성 (필수)

| 요소 | 체크 |
|------|------|
| `<button>` | focusable, Enter/Space 동작 |
| Icon-only 버튼 | `aria-label` 필수 |
| Modal | focus trap, Esc로 닫기, role="dialog", aria-labelledby |
| Tooltip | `aria-describedby` |
| Form | `<label htmlFor>` 또는 `aria-label` |
| Live region (toast) | `role="status"` 또는 `aria-live` |
| 색상만으로 의미 전달 X (아이콘/텍스트 병기) | |

---

## 5. forwardRef

DOM 노드를 외부에서 접근할 가능성이 있으면 (focus, scroll 등):
```tsx
export const Input = forwardRef<HTMLInputElement, InputProps>(
  function Input({ ...props }, ref) {
    return <input ref={ref} {...props} />;
  }
);
```

React 19부터는 ref도 일반 prop처럼 받을 수 있음 — 신규 컴포넌트는 신규 방식 사용:
```tsx
export function Input({ ref, ...props }: InputProps & { ref?: Ref<HTMLInputElement> }) {
  return <input ref={ref} {...props} />;
}
```

---

## 6. 스타일링 전략

(라이브러리 결정 전. 결정 후 본 섹션 갱신)

후보:
- **Tailwind** — 토큰을 `tailwind.config.js`에 매핑, JIT 빌드, 클래스 length 트레이드오프
- **CSS Modules** — 컴포넌트와 강한 결합, 타입 안전 (`*.module.css.d.ts`)
- **vanilla-extract** — 타입 안전 CSS-in-TS, zero runtime

공통 원칙:
- inline style은 동적 값(progress bar width 등)에만 한정
- 글로벌 CSS는 `app/styles/`에만

---

## 7. 컴포넌트 추가 절차

1. [`/docs/design-system.md §3`](../../../../docs/design-system.md) 인벤토리에 등재 의도 확인
2. `shared/ui/{Name}/` 디렉토리 생성
3. props는 minimal하게 → 이후 필요시 확장
4. 단위 테스트: 핵심 동작 + 접근성 (axe-core 권장)
5. 스토리북(옵션) 작성 — light/dark, 상태별
6. PR에 스크린샷 첨부

---

## 8. 컴포넌트 인벤토리

전체 목록은 [`/docs/design-system.md §3`](../../../../docs/design-system.md) 참조.

본 디렉토리에는 **shared (도메인 비종속)** 컴포넌트만:
- Button, IconButton, Link
- Input, Textarea, Select, Combobox, Checkbox, Radio, Switch
- Modal, Drawer, Popover, Tooltip, ConfirmDialog
- Toast, Alert
- Badge, Tag, Avatar, Card, Skeleton, EmptyState
- Tabs, Breadcrumb, Pagination
- StatusBadge (상태 코드 → 색상 매핑은 도메인 종속이지만, **상태 컬러맵 자체를 props로 받는 일반 컴포넌트**로 설계해서 shared에 둠. 도메인 매핑은 호출부에서)

---

## 9. 안티패턴

- ❌ 도메인 타입(`Session`, `User`) import
- ❌ axios/fetch 호출
- ❌ 컴포넌트 내부에서 라우팅 (`useNavigate` 사용 X — props로 onClick 받기)
- ❌ 컴포넌트가 자기 자신을 export하면서 다른 컴포넌트도 같이 export (단일 책임 위반)
- ❌ 한 컴포넌트가 너무 많은 variant (5개 이상은 분할 검토)
