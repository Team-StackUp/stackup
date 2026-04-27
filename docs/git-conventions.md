# Git 컨벤션

> 브랜치 전략, 커밋 메시지, PR 규약. 본 컨벤션은 `.github/PULL_REQUEST_TEMPLATE.md` 와 `.github/workflows/lint.yml` 의 전제와 일치한다.

---

## 1. 브랜치 전략

### 1.1 브랜치 종류
| 브랜치 | 용도 | 머지 대상 |
|--------|------|-----------|
| `main` | 배포 가능한 최신 상태 | (보호) |
| `develop` | 통합 개발 (옵션 — 본 프로젝트 기준 직접 main 사용 가능) | main |
| `feature/*` | 신규 기능 | main (또는 develop) |
| `fix/*` | 버그 수정 | main |
| `hotfix/*` | 긴급 운영 수정 | main + cherry-pick to develop |
| `chore/*` | 비기능 변경 (lint, deps, doc) | main |
| `refactor/*` | 동작 변경 없는 구조 개선 | main |
| `docs/*` | 문서만 | main |

### 1.2 명명
```
feature/<short-kebab-case-summary>
feature/us-13-session-create
fix/session-status-transition
chore/add-renovate-config
```

- US 번호가 있으면 포함 (`feature/us-XX-...`)
- 한글·공백 금지

### 1.3 브랜치 수명
- feature: ≤ 1주 (장기 시 재분기 검토)
- 매일 main → feature 병합으로 충돌 최소화 (`git rebase main` 권장)

---

## 2. 커밋 메시지 (Conventional Commits 기반, 한국어 본문 OK)

### 2.1 포맷
```
<type>(<scope>): <subject>

<body — optional, 줄바꿈으로 분리>

<footer — optional, BREAKING CHANGE / Refs>
```

### 2.2 Type
| type | 의미 |
|------|------|
| `feat` | 신규 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서만 |
| `style` | 포맷·세미콜론 (동작 변경 없음) |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드, 의존성, 도구 |
| `perf` | 성능 개선 |
| `ci` | CI 설정 |

### 2.3 Scope (선택)
- 도메인: `auth`, `session`, `resume`, `repo`, `feedback`
- 레이어: `frontend`, `backend`, `ai`, `infra`
- 모듈: `ui`, `api`, `db`

### 2.4 Subject 규칙
- 한글 OK (현재 프로젝트 컨벤션)
- 명령형 / 현재시제 ("추가", "수정")
- 끝에 마침표 ✗
- 50자 이내

### 2.5 예시 (현 프로젝트 기존 commit과 일관)
```
chore: 프로젝트 초기 설정 - infra
chore: PR 병합 전 lint 검사하도록 github action 설정
chore: pull request template 생성
feat(session): US-13 면접 세션 생성 API 구현
fix(auth): refresh token 회전 시 race condition 해결
docs(architecture): RabbitMQ 토폴로지 다이어그램 추가
```

---

## 3. Pull Request

### 3.1 작성 시점
- WIP 단계는 **Draft PR** 활용
- Ready for review 표시 전: 본인 self-review 1회

### 3.2 제목
```
<type>(<scope>): <subject>
```
커밋 메시지와 동일 포맷. squash merge 시 그대로 commit이 됨.

### 3.3 본문 (PR 템플릿)
`.github/PULL_REQUEST_TEMPLATE.md` 가 자동 적용. 다음 섹션 권장:

```markdown
## 작업 내용
- 변경 사항 bullet
- US-XX, US-YY

## 변경 이유 / 배경
(왜 이 변경이 필요한지)

## 테스트
- [ ] 단위 테스트 추가/수정
- [ ] 로컬에서 시나리오 확인
- [ ] (선택) 스크린샷 첨부

## 영향 범위
- DB 마이그레이션: 있음/없음
- API contract 변경: 있음/없음
- 환경변수 추가/변경: 있음/없음

## 리뷰어 체크포인트
(특별히 봐줬으면 하는 부분)
```

### 3.4 라벨 (gh repo 설정 후)
- `area/frontend`, `area/backend`, `area/ai`, `area/infra`, `area/docs`
- `type/feat`, `type/fix`, `type/chore`
- `priority/high|medium|low`

### 3.5 머지 정책
- **Squash merge** 기본 (히스토리 깔끔)
- 단, 의미 있는 커밋이 여러 개라면 일반 merge도 허용
- main 직접 푸시 금지
- 1명 이상 approve + CI green 필수

---

## 4. 충돌 해결

- 본인 브랜치에서 `git rebase main` (merge 대신)
- 충돌 발생 시 양쪽 변경의 의도를 확인 (단순 텍스트 머지 X)
- DB 마이그레이션 충돌: 버전 번호 재할당 + 순서 정합성 확인

---

## 5. 보호 정책 (main)

- 직접 push 금지
- PR + CI green + 1 approve 필수
- 일정 시점부터 force-push 금지

---

## 6. .gitignore 정책

루트 `.gitignore`는 OS·에디터 노이즈만:
- `.DS_Store`, `Thumbs.db`
- `.idea/`, `.vscode/` (단, 팀 공유 설정은 `.vscode/settings.json` 화이트리스트 등록)

언어별 무시 패턴은 각 레이어 `.gitignore`로 분리:
- `frontend/.gitignore` — `node_modules`, `dist`, `.env*`
- `backend/.gitignore` — `build/`, `*.class`, `.gradle/`
- `ai/.gitignore` — `.venv/`, `__pycache__/`, `*.pyc`, `.env`

---

## 7. 큰 파일 정책

- 10MB 초과 파일 커밋 금지 (Git LFS 또는 S3로 분리)
- `*.pdf` 산출물은 `docs/`에만 OK
- 스크린샷은 PR 본문에 첨부 (커밋 X)

---

## 8. Co-author 표기

페어 프로그래밍 / 도구 협업 시:
```
Co-Authored-By: 정준모 <jjm@example.com>
Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 9. 릴리스·태그

- 태그 형식: `vX.Y.Z` (semver)
- Phase 별 마일스톤 태그: `phase-1-mvp`, `phase-2-voice`
- CHANGELOG는 `release/` 노트에 자동 생성 (Phase 2 이후 도입)

---

## 10. 자주 하는 실수 방지

- ❌ `.env` 커밋 → `.gitignore` 확인
- ❌ `node_modules`, `build/` 커밋 → `.gitignore`
- ❌ 디버그용 `console.log`, `System.out.println` 잔존 → lint로 차단
- ❌ 마이그레이션 파일 수정 (이미 적용됨) → 신규 V 추가
- ❌ 한 PR에 무관한 변경 다수 → 분리
