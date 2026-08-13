# STACK-UP Frontend

React 19 + TypeScript + Vite SPA. **FSD(Feature-Sliced Design)** 구조를 따릅니다.

```
src/
├── app/        # 부트스트랩 (providers, router, styles)
├── pages/      # 라우트 단위 페이지 (composition만)
├── widgets/    # 페이지를 구성하는 큰 UI 블록
├── features/   # 사용자 행동 단위 (auth, interview, feedback, …)
├── domain/     # 도메인 타입 · 상수 · 순수 함수
└── shared/     # 디자인 시스템 컴포넌트, API 클라이언트, 훅
```

의존 방향은 `app → pages → widgets → features → domain → shared` 단방향만 허용합니다.

## 실행

```bash
npm install
npm run dev        # 개발 서버 (:5173)
npm run build      # 타입 체크 + 프로덕션 빌드
npm run lint       # ESLint
npx vitest run     # 단위 테스트
npm run openapi    # ../backend/openapi.json → shared/api/generated.ts 타입 재생성
```

환경 변수는 `.env.example`을 `.env.local`로 복사해 채웁니다 (`VITE_` 접두 필수).

## 주요 스택

- **서버 상태**: TanStack Query v5 · **라우팅**: React Router v7
- **스타일**: Tailwind CSS v4 + 당근 SEED Design 토큰 (컬러 · radius · shadow, 다크모드 포함)
- **API 타입**: openapi-typescript로 백엔드 스펙에서 자동 생성 — 런타임 무의존
- **테스트**: Vitest + Testing Library

자세한 규약은 [`CLAUDE.md`](./CLAUDE.md)와 [루트 README](../README.md)를 참고하세요.
