# 프론트엔드 타입 시스템 — TypeScript

> StackUp 프론트엔드의 TypeScript 타입 규약. **타입은 사용되는 레이어에 산다.** 이름의 접미사가 책임 경계를 선언한다.
>
> 상위 컨텍스트 — [coding-conventions.md](./coding-conventions.md) (언어 공통 규약), [`frontend/CLAUDE.md`](../frontend/CLAUDE.md) (FSD 구조)
> 적용 범위 — [`frontend/src/`](../frontend/src/)

---

## 목차

1. [원칙](#1-원칙)
2. [접미사 ↔ 레이어 매트릭스](#2-접미사--레이어-매트릭스)
3. [레이어별 타입 흐름](#3-레이어별-타입-흐름)
4. [`type` vs `interface`](#4-type-vs-interface)
5. [정의 위치](#5-정의-위치)
6. [shared 승격 임계](#6-shared-승격-임계)
7. [안티패턴](#7-안티패턴)

---

## 1. 원칙

- **PascalCase + 의미 있는 접미사**. 접미사가 타입의 책임·레이어를 신고한다.
- `I` / `T` Hungarian prefix 금지 — TypeScript 컴파일러가 `type` / `interface` 를 충분히 구분한다.
- **단방향 흐름**: `Dto → Entity → Model → Props`. 역방향 import 금지.
- **전역 `types/` 폴더 금지**. 슬라이스 내부에서 시작, 진짜 공유될 때만 끌어올린다.

---

## 2. 접미사 ↔ 레이어 매트릭스

| 접미사 | 레이어 | 책임 | 예 |
|---|---|---|---|
| `XxxDto` | `shared/api/` (OpenAPI 자동 생성) | 백엔드 wire format 스냅샷. 수정 금지 | `SessionDto`, `UserDto` |
| `XxxRequest` / `XxxPayload` | API 호출부, 이벤트 publisher | HTTP 요청 body, RabbitMQ 메시지 payload | `CreateSessionRequest`, `ResumeAnalyzedPayload` |
| `XxxResponse` | API 호출부 | HTTP 응답 raw shape (수동 정의 시) — **함수 반환에 사용 ✗** | `LoginResponse` |
| `Xxx` (접미사 없음) | `domain/{slice}/model/` | 비즈니스 엔터티. Dto에서 매핑됨 | `User`, `Session`, `Resume` |
| `XxxResult` | 도메인 / 유즈케이스 함수 반환 | 비즈니스 로직 출력 (HTTP 비종속) | `AnalysisResult`, `ValidationResult` |
| `XxxModel` | `features/*/ui/`, `widgets/*/ui/` | UI 표시 전용 가공 데이터 | `SessionListItemModel`, `ServiceCardModel` |
| `XxxProps` | 컴포넌트 옆 | React props | `ButtonProps`, `HomeHeroProps` |
| `XxxState` | store / state machine | **명시적** 상태 모델. 단발 `useState` shape 에 사용 ✗ | `AuthState`, `SessionMachineState` |
| `XxxOptions` | 함수·훅 설정 | 옵셔널 인자 묶음 | `UseTypewriterOptions` |
| `XxxId` | 어디든 | branded identifier | `type SessionId = number & { readonly __brand: 'SessionId' }` |

---

## 3. 레이어별 타입 흐름

```
 [Backend OpenAPI]
        │ openapi-typescript 자동 생성
        ▼
 shared/api/generated.ts      ──  XxxDto
        │ 매퍼 함수 (toUser, toSession)
        ▼
 domain/{slice}/model/        ──  Xxx (Entity)
        │ feature / widget 에서 UI 가공
        ▼
 features|widgets/*/ui/       ──  XxxModel
        │ props 로 주입
        ▼
 React Component              ──  XxxProps
```

**경계 규칙**:

- `XxxDto` 는 `shared/api/` 밖으로 새지 않는다. UI · 도메인이 Dto 를 직접 import 하면 anti-corruption layer 가 무너진다.
- `Xxx` (Entity) 는 `domain/` 이 소유. features / widgets 는 read-only 로 참조한다.
- `XxxModel` 은 슬라이스 내부에 머문다. 다른 슬라이스로 export 하지 않는다 — UI 표시 형태가 누수되면 변경 비용이 폭증한다.

---

## 4. `type` vs `interface`

`type` 기본값. `interface` 는 두 경우에만:

1. 클래스가 `implements` 할 때 (`ErrorBoundary` 등)
2. declaration merging 필요 시 (외부 라이브러리 augmentation)

```ts
// 좋음
type SessionStatus = 'READY' | 'IN_PROGRESS' | 'COMPLETED';
type CreateSessionRequest = { userId: UserId; mode: SessionMode };
type AnalysisResult = { score: number; suggestions: string[] };

// 좋음 — implements 가 필요한 케이스
interface ErrorBoundaryProps { children: ReactNode; fallback: ReactNode }
class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> { ... }
```

---

## 5. 정의 위치

| 무엇 | 어디 |
|---|---|
| 컴포넌트 props (단일 사용) | `{Component}.tsx` 상단, **export 안 함** |
| 외부에서도 import 되는 props | `{Component}.types.ts` |
| 도메인 엔터티 | `domain/{slice}/model/types.ts` |
| 슬라이스 내 `XxxModel` | `features/{slice}/model/types.ts` 또는 `ui/` 내부 |
| API `XxxDto` | `shared/api/generated.ts` (OpenAPI 재생성으로만 갱신) |

**전역 `src/types/` 폴더는 만들지 않는다.** 슬라이스 내부에서 시작하고, 진짜 공유될 때만 끌어올린다.

---

## 6. shared 승격 임계

타입을 `shared/` 로 올리는 시점은 **서로 다른 슬라이스 3곳 이상에서 동일한 shape 이 반복될 때**. 그 전엔 슬라이스 안에 산다.

> "언젠가 쓸지도 모르니 미리 shared 로" 는 항상 잘못된 추상화로 끝난다.

---

## 6.5 AI 텍스트 필드 포맷 계약 (A5)

서버가 주는 AI 생성 텍스트는 필드별로 포맷이 계약돼 있다 (정본: [`messaging.md §5.4·§5.11`](./messaging.md)):

| 포맷 | 필드 | 렌더 |
|---|---|---|
| **GFM 마크다운** | 분석 산출물(`documentPath` 문서), `answerCoaching[].modelAnswer`/`answerRewrite` | `shared/ui/Markdown` (lazy + sanitize) |
| **일반 텍스트** | 피드백 요약·`highlights[]`·`studyPlan[]`·패널 `detail`·질문 텍스트·`coachingComment` 등 나머지 전부 | plain (`whitespace-pre-wrap`) — `HighlightedText` 부분 문자열 매칭·델타 스트리밍과의 충돌을 막기 위한 계약 |

사용자 입력(내 답변 등)은 어떤 경우에도 마크다운으로 렌더하지 않는다.

## 7. 안티패턴

- ❌ `XxxResponse` 를 함수 반환 타입으로 사용 — `XxxResult` 로.
- ❌ UI 컴포넌트가 `SessionDto` 를 props 로 받음 — `SessionListItemModel` 로 매핑.
- ❌ 로컬 `useState<{ ... }>` shape 에 `XxxState` 이름 붙이고 export.
- ❌ `export type Props = { ... }` 같은 generic export — `{Component}Props` 로.
- ❌ `src/types/` 또는 `src/shared/types/` 전역 폴더 (junk drawer 화).
- ❌ `IXxx`, `TXxx` Hungarian prefix.
- ❌ `Dto` 가 `features/` / `widgets/` / `domain/` 에서 import 되는 경우 — boundary 위반.
