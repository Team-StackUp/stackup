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
domain/*  →  domain/*       ✓ (다른 도메인 참조 가능, 단 순환 금지)
domain/*  →  features/*     ✗
domain/*  →  pages/*, app/* ✗
```

타입 자체는 다른 도메인을 참조 가능 (예: `Session`이 `User`를 알고 있음).

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
