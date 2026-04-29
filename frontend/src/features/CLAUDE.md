# `features/` — 기능 슬라이스 레이어

> "사용자가 할 수 있는 행동" 단위. 한 feature = 자체적인 UX 단위.

상위: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 1. feature가 되어야 하는 것 / 아닌 것

**feature O**:
- "이력서를 업로드한다" — `features/resume`
- "GitHub 레포를 등록한다" — `features/repo`
- "면접 세션을 진행한다" — `features/interview`
- "피드백 리포트를 본다" — `features/feedback`
- "GitHub OAuth로 로그인한다" — `features/auth`

**feature X**:
- "Button을 클릭한다" — UI 컴포넌트는 `shared/ui`
- "User 도메인" — 모델은 `domain/user`
- "API 호출 자체" — 인프라는 `shared/api`

**판단 기준**: User Story (US-XX) 1개와 대응되는 사용자 액션이라면 feature로.

---

## 2. 슬라이스 구조

```
features/{name}/
├── ui/                 # 컴포넌트 (LoginButton, ResumeUploader, ...)
├── model/              # 훅, store (useAuth, useResumeUpload, ...)
├── api/                # 서버 호출 (loginWithGithub, uploadResume, ...)
├── lib/                # 슬라이스 내 유틸 (file 검증, 포맷 등)
└── index.ts            # public API
```

### Public API (index.ts)
외부에서 사용할 것만 export. 내부 구현은 export하지 않는다.
```ts
// features/resume/index.ts
export { ResumeUploader } from './ui/ResumeUploader';
export { ResumeList } from './ui/ResumeList';
export { useResumeUpload } from './model/useResumeUpload';
export type { Resume } from './model/types';
```

---

## 3. 의존성 규칙

```
features/{X}  →  domain/*, shared/*    ✓
features/{X}  →  features/{Y}          ✗ (다른 feature import 금지)
features/{X}  →  pages/*, app/*        ✗
```

다른 feature가 필요하면:
- 공통 부분을 `domain/`으로 추출
- 또는 페이지에서 두 feature를 composition으로 연결

---

## 4. 상태 관리

- **server state** (서버에서 가져오는 데이터): TanStack Query
- **로컬 UI state**: `useState` / `useReducer`
- **feature 내 공유 state**: feature 내부 Context 또는 zustand store
- **전역 state**: 최소화. 인증·테마 정도만 (`features/auth`, `app/providers/ThemeProvider`)

→ 의도적으로 Redux 같은 글로벌 store는 피한다 (FSD와 잘 맞지 않음).

---

## 5. feature 인벤토리 (현재 + 계획)

| Feature | 책임 | 관련 US |
|---------|------|---------|
| `auth` | GitHub OAuth, 토큰 관리, 로그아웃, 동의 | US-01, US-02, US-03, US-04 |
| `resume` | 이력서 업로드, 목록, 삭제 | US-05, US-06 |
| `repo` (계획) | GitHub 레포 가져오기/등록/삭제 | US-07, US-08 |
| `analysis` (계획) | 분석 상태 표시, 재분석 | US-11, US-12 |
| `interview` | 세션 생성·진행·종료, 메시지, 음성 | US-13~22 |
| `feedback` | 피드백 리포트, 점수, 키워드 | US-24, US-25 |
| `history` (계획) | 세션 히스토리 목록·상세, 통계 | US-15, US-16, US-26, US-27 |

---

## 6. UI 패턴 표준

각 feature의 UI는 [`/docs/ui-patterns.md`](../../../docs/ui-patterns.md) 의 4-state(loading/empty/error/success), 폼 검증, 파괴적 액션 confirmation 규약을 따른다.

---

## 7. 신규 feature 추가 절차

1. `features/{name}/` 디렉토리 생성 (위 구조)
2. `index.ts`에 public API 정의
3. ESLint `no-restricted-imports` 룰에 슬라이스 추가 (도입 시)
4. 페이지에서 import해서 조립
5. 본 문서 §5 인벤토리에 등록

---

## 8. 안티패턴

- ❌ `features/auth/ui/LoginButton.tsx` 를 다른 feature에서 직접 import
- ❌ feature 내부에 라우팅 결정 로직 (페이지가 결정)
- ❌ 한 feature에 무관한 기능 섞기 ("user_management" 같은 거대 feature)
- ❌ feature 간 store 공유 (필요하면 domain으로 승격)
