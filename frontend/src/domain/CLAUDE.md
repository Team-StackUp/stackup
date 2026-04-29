# `domain/` — 도메인 모델 레이어

> 도메인 개념(User, Session, RAG)의 **타입·상수·순수 함수**. UI/API/사이드이펙트 금지.

상위: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 1. 책임

각 도메인 슬라이스는 다음을 제공:

- **타입 정의**: 백엔드 응답 타입을 도메인 모델로 변환한 형태
- **상수**: ENUM 값, 라벨, 색상 매핑
- **순수 함수**: 점수 계산, 상태 전이 가능 여부 판단, 표시 포맷
- **타입 가드** / 변환 함수

**비책임**: React 컴포넌트, API 호출, 사이드이펙트.

---

## 2. 슬라이스 구조

```
domain/{name}/
├── model/         # 타입 정의 (zod schema, type aliases)
├── constants/     # ENUM, 라벨 매핑
├── lib/           # 순수 함수
└── index.ts       # public API
```

---

## 3. 도메인 인벤토리

| 도메인 | 핵심 타입 | 위치 |
|--------|-----------|------|
| `user` | `User`, `UserConsent`, `JobCategory` | `domain/user` |
| `session` | `InterviewSession`, `SessionStatus`, `InterviewType`, `Message`, `MessageRole` | `domain/session` |
| `rag` | `AnalyzedDocument`, `DocStatus`, `AnalysisState` | `domain/rag` |

---

## 4. 타입 컨벤션

### 4.1 백엔드 ENUM과 정확히 매칭
```ts
// domain/session/model/types.ts
export const SESSION_STATUS = {
  READY: 'READY',
  IN_PROGRESS: 'IN_PROGRESS',
  INTERRUPTED: 'INTERRUPTED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const;
export type SessionStatus = typeof SESSION_STATUS[keyof typeof SESSION_STATUS];
```

→ 백엔드 ENUM 변경 시 본 파일 수동 동기화 (또는 OpenAPI 자동 생성 결과를 그대로 re-export).

### 4.2 한국어 라벨은 constants에
```ts
// domain/session/constants/labels.ts
export const SESSION_STATUS_LABEL: Record<SessionStatus, string> = {
  READY: '대기 중',
  IN_PROGRESS: '진행 중',
  INTERRUPTED: '중단됨',
  COMPLETED: '완료',
  CANCELLED: '취소됨',
};
```

### 4.3 색상 매핑
디자인 시스템의 status 색상은 `domain/{name}/constants/colors.ts`에 매핑:
```ts
export const SESSION_STATUS_COLOR: Record<SessionStatus, BadgeVariant> = {
  READY: 'neutral',
  IN_PROGRESS: 'warning',
  INTERRUPTED: 'warning',
  COMPLETED: 'success',
  CANCELLED: 'disabled',
};
```

`shared/ui/StatusBadge` 같은 컴포넌트가 이 매핑을 사용.

---

## 5. 순수 함수 예시

```ts
// domain/session/lib/transitions.ts
export function canStart(session: InterviewSession): boolean {
  return session.status === 'READY';
}

export function canEnd(session: InterviewSession): boolean {
  return session.status === 'IN_PROGRESS';
}

export function reachedLimit(session: InterviewSession): boolean {
  return session.totalQuestionCount >= session.maxQuestions;
}
```

이런 로직을 컴포넌트나 훅에 흩지 않는다 — domain에 모은다.

---

## 6. 의존성 규칙

```
domain/*  →  shared/*       ✓
domain/*  →  domain/*       ⚠️ 단방향만 ✓, 순환 ✗
domain/*  →  features/*     ✗
domain/*  →  pages/*, app/* ✗
```

타입 자체는 다른 도메인을 참조 가능 (예: `Session`이 `User`를 알고 있음).

### 6.1 순환 참조 절대 금지

도메인 간 의존성은 **단방향 그래프(DAG)** 여야 한다. 순환이 발생하면 타입 추론 무한 루프, 모듈 번들 분리 불가, 테스트 시 mock 지옥 등 실질적 피해가 크다.

**나쁜 예 (순환)**:
```ts
// domain/session/model/types.ts
import type { User } from '@/domain/user';
export type Session = { user: User; ... };

// domain/user/model/types.ts
import type { Session } from '@/domain/session';   // ❌ 순환!
export type User = { recentSessions: Session[]; ... };
```

**좋은 예 (단방향)**:
```ts
// domain/user (의존 없음, 가장 아래)
export type User = { id: number; githubUsername: string; ... };

// domain/session (user에 의존)
import type { User } from '@/domain/user';
export type Session = { userId: User['id']; ... };  // ID만 보유

// "user의 최근 세션" 같은 derived view는 features/history에서 조합
```

### 6.2 도메인 의존성 그래프 (현재)

```
session  →  user
rag      →  user
```

새 도메인 추가 시 본 그래프를 갱신하고, **위쪽에 위치한 도메인이 아래쪽을 import**하는 방향만 허용.

### 6.3 자동 검증

ESLint `no-restricted-imports` 또는 `eslint-plugin-boundaries` 도입 시:
```js
// eslint.config.js (예시)
{
  rules: {
    'boundaries/element-types': ['error', {
      default: 'disallow',
      rules: [
        { from: 'domain-user',    allow: ['shared'] },
        { from: 'domain-session', allow: ['shared', 'domain-user'] },
        { from: 'domain-rag',     allow: ['shared', 'domain-user'] },
      ],
    }],
  },
}
```

CI 단계에서 자동으로 위반 차단. 백엔드는 ArchUnit 사용 ([`/backend/CLAUDE.md`](../../../CLAUDE.md)).

---

## 7. zod 사용 (도입 시)

서버 응답을 런타임 검증하고 싶다면:
```ts
import { z } from 'zod';

export const SessionSchema = z.object({
  id: z.number(),
  title: z.string().nullable(),
  status: z.enum([...Object.values(SESSION_STATUS)]),
  // ...
});
export type Session = z.infer<typeof SessionSchema>;
```

API 응답 boundary에서만 검증 (호출 시마다 검증하면 비용).

---

## 8. 안티패턴

- ❌ React 컴포넌트, 훅 (UI는 features/shared/ui)
- ❌ axios·fetch 호출
- ❌ 글로벌 변수, mutable state
- ❌ `Date.now()`, `Math.random()` 같은 비결정적 호출 (테스트 불가)
- ❌ 백엔드 응답 그대로 노출 (필요하면 도메인 타입으로 변환)
- ❌ **도메인 간 순환 import** — 발견 즉시 ID 참조로 분해 (§6.1)
- ❌ 한 도메인이 너무 많은 다른 도메인 import — 책임 경계 재검토 필요
