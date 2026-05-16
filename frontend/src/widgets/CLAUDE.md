# `widgets/` — 위젯 슬라이스 레이어

> 페이지를 구성하는 "큰 UI 블록" 단위. 한 widget = 자체적으로 의미를 가지는 섹션·영역.

상위: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 1. widget이 되어야 하는 것 / 아닌 것

**widget O**:
- "사이트 네비게이션" — `widgets/site-nav`
- "사이트 푸터" — `widgets/site-footer`
- "홈 히어로 섹션" — `widgets/home-hero`
- "서비스 카드 그리드" — `widgets/home-services`
- "FAQ 아코디언 섹션" — `widgets/home-faq`
- "워크스페이스 사이드바" (예정) — `widgets/workspace-sidebar`
- "면접 진행 컨트롤 바" (예정) — `widgets/interview-control`

**widget X**:
- "Button 자체" — `shared/ui/Button`
- "이력서를 업로드한다" — `features/resume` (사용자 액션 흐름)
- "Session 도메인 모델" — `domain/session`

**판단 기준**:
- 페이지에 박혔을 때 **자체적으로 의미를 가지는 UI 블록**이면 widget.
- 작은 범용 컴포넌트라면 `shared/ui`.
- 사용자 액션(클릭 → API 호출 → 상태 변경)이 본질이라면 `features/`.

---

## 2. 슬라이스 구조

```
widgets/{name}/
├── ui/                  # 컴포넌트 (HomeHero, SiteNav, ...)
│   └── {Widget}.tsx
├── model/               # widget 단위 훅·상태 (있다면)
├── lib/                 # widget 내부 유틸 (있다면)
└── index.ts             # public API
```

**대부분의 widget은 `ui/` + `index.ts` 두 개로 끝난다.** `model/`·`lib/`는 필요해질 때만 추가.

### Public API (index.ts)
메인 컴포넌트만 export. 내부 sub-component는 export 하지 않는다.
```ts
// widgets/home-hero/index.ts
export { HomeHero } from './ui/HomeHero'

// ui/Laptop.tsx, ui/ScreenContent.tsx 는 내부 구현 — re-export 안 함
```

슬라이스 폴더는 **kebab-case**, 컴포넌트 export 는 **PascalCase**.

---

## 3. 의존성 규칙

```
widgets/{X}  →  features/*, domain/*, shared/*    ✓
widgets/{X}  →  widgets/{Y}                       ✗ (다른 widget import 금지)
widgets/{X}  →  pages/*, app/*                    ✗
```

위젯끼리 import가 필요하면:
- 공통 UI 조각을 `shared/ui` 로 추출
- 또는 페이지에서 composition 으로 연결 (페이지가 widget 조립 책임)

---

## 4. 명명 규칙

| Prefix | 범위 | 예 |
|---|---|---|
| `site-*` | 사이트 전역 (여러 페이지 재사용) | `site-nav`, `site-footer` |
| `home-*` | 홈페이지 전용 | `home-hero`, `home-services`, `home-faq` |
| `workspace-*` | 워크스페이스 페이지 전용 | `workspace-profile-card`, `workspace-section` |
| `interview-*` (예정) | 면접 페이지 전용 | `interview-control`, `interview-transcript` |

> prefix 가 페이지 종속을 명시적으로 표현. 한 페이지에서만 쓰이는 widget 도 정상.

---

## 5. widget 인벤토리

| Widget | 책임 | 사용 페이지 |
|---|---|---|
| `site-nav` | 상단 네비게이션, 스크롤 시 배경 전환 | 전역 |
| `site-footer` | 푸터 (브랜드, 링크 컬럼, 카피라이트) | 전역 |
| `home-hero` | 노트북 목업 + 타이핑 인트로 (`useTypewriter`) | `/` |
| `home-services` | 서비스 카드 3개 그리드 | `/` |
| `home-quote` | 다크 그린 quote · 팀 크레딧 | `/` |
| `home-faq` | FAQ 아코디언 (`<details>` 기반) | `/` |
| `home-cta` | 풀-블리드 CTA 배너 | `/` |
| `workspace-profile-card` | `useAuth().user` 기반 프로필 카드 (avatar + 핸들 + email + 연결 상태) | `/workspace` |
| `workspace-section` | 타이틀·설명·우측 액션·children 슬롯 컨테이너 (도메인 무관) | `/workspace` |

---

## 6. widget vs feature 비교

| 질문 | widget | feature |
|---|---|---|
| 본질 | 페이지 영역을 채우는 **UI 구성** | 사용자가 수행하는 **액션 흐름** |
| 서버 호출 | 보통 없음 (data 는 props 로 받음) | 있음 (mutation / query) |
| User Story 매핑 | 없음 (UI 표현 단위) | US-XX 1개 |
| 상태 | UI 로컬 상태 정도 | 도메인 상태 / 서버 상태 |
| 예 | `home-hero`, `site-nav` | `auth`, `resume`, `interview` |

같은 영역이라도 **데이터 표시만**이면 widget, **사용자 액션 흐름**이 있으면 feature. widget 이 feature 의 훅을 끌어와 페이지 한 구역에 박는 패턴은 정상 (예: `widgets/interview-control` 이 `features/interview` 의 mutation 훅 사용).

---

## 7. 타입 사용

widget UI 컴포넌트가 받는 데이터는 [`/docs/frontend-types.md`](../../../docs/frontend-types.md) 의 단방향 흐름을 따른다.

```
domain/{slice}/model/ (Entity)  →  widget 내부에서 XxxModel 로 가공  →  컴포넌트 Props
```

`XxxDto` 를 widget 안에서 직접 import 하면 boundary 위반. 매핑은 `features/*/api/` 또는 `domain/*/model/` 에서.

---

## 8. 신규 widget 추가 절차

1. `widgets/{name}/ui/{Widget}.tsx` 생성 (PascalCase 파일·컴포넌트명)
2. `widgets/{name}/index.ts` 에 메인 컴포넌트만 export
3. 페이지에서 import 해서 조립 (`pages/Xxx/ui/XxxPage.tsx`)
4. 본 문서 §5 인벤토리 등록

---

## 9. 안티패턴

- ❌ widget 이 다른 widget 을 직접 import — 페이지에서 composition 으로
- ❌ widget 이 라우팅 결정 (`useNavigate` 등) — 페이지 책임
- ❌ widget 이 `XxxDto` 직접 사용 — [`/docs/frontend-types.md`](../../../docs/frontend-types.md) 단방향 흐름 위반
- ❌ widget 내부 sub-component 를 `index.ts` 에서 re-export — 캡슐화 깨짐
- ❌ "재사용성을 위한" widget 미리 만들기 — 한 페이지에서만 써도 widget 이 되는 게 정상. 재사용은 필요해지면.
- ❌ widget 에서 직접 서버 호출 — `features/*/api/` 또는 `domain/*` 을 경유
