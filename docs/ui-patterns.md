# UI 패턴

> 반복되는 UX 패턴과 상태 처리 규약. 컴포넌트가 아닌 **상호작용 시나리오** 단위.

---

## 1. 비동기 데이터 4-state 처리

모든 비동기 데이터 표시 영역은 다음 4가지 상태를 명시적으로 핸들링한다.

| 상태 | 컴포넌트 | 사용자에게 보여줄 것 |
|------|----------|------------------------|
| `loading` | `Skeleton` | 실제 컴포넌트 골격을 흉내낸 placeholder |
| `empty` | `EmptyState` | 아이콘 + 안내 + CTA ("이력서를 업로드하세요") |
| `error` | `Alert` 또는 `EmptyState` (variant=error) | 사용자 행동 가능한 메시지 + 재시도 버튼 |
| `success` | 실제 데이터 | |

**구현 패턴**: `frontend/src/shared/lib/AsyncBoundary` 사용.
```tsx
<AsyncBoundary
  pendingFallback={<ResumeListSkeleton />}
  rejectedFallback={({ error, reset }) => <ResumeListError error={error} onRetry={reset} />}
>
  <ResumeList />
</AsyncBoundary>
```

`empty`는 데이터 fetch 후 컴포넌트 내부에서 분기 (boundary 책임 아님).

---

## 2. 분석 상태 표시 (US-11)

이력서·레포 분석 진행 상태는 다음 단계로 표현:

```
QUEUED  →  ANALYZING  →  ANALYZED
 (대기)    (진행 중)      (완료)
                        ↘
                          FAILED
```

UI 표현:
- **List 화면**: `StatusBadge` (4색 매핑)
- **Detail 화면**: 상단에 `AnalysisStateIndicator` (4단계 progress bar) + 마지막 메시지 표시
- **푸시 알림**: SSE → Toast (`ANALYZED` 시 "분석이 완료되었습니다" + "면접 시작" CTA)

`FAILED` 시 재시도 버튼 + `errorMessage` 노출.

---

## 3. 폼 검증 패턴

### 단일 필드 (즉시 검증)
- 입력 시점에는 검증 안 함 (사용자 방해)
- `onBlur` 시점에 검증 + 에러 표시
- 에러는 input 하단 `--color-danger` 텍스트

### 폼 제출 (집합 검증)
- 제출 시 모든 필드 재검증
- 첫 번째 에러 필드로 자동 focus + scroll
- 서버 검증 실패 (422) → 해당 필드에 `details.field` 매핑

### 필수 표시
- 라벨 옆 `*` (빨강) — 모든 필수 필드
- 안내문구는 placeholder가 아닌 helper text로

---

## 4. 파괴적 액션 (Confirmation)

다음은 반드시 `ConfirmDialog`로 재확인:
- 회원 탈퇴
- 이력서 / 레포 삭제 (활성 세션에서 사용 중이면 추가 경고)
- 진행 중인 면접 세션 취소
- 분석 결과 재분석 (기존 결과 교체)

`ConfirmDialog` 메시지 톤:
> "정말 삭제하시겠습니까?"
> "이 작업은 되돌릴 수 없습니다." (필요 시)
> 확인 버튼은 `danger` variant + 동작 명시 ("삭제", "탈퇴", "취소")

---

## 5. 토스트 사용 가이드

| 상황 | Toast 사용 여부 | 대안 |
|------|-----------------|------|
| API 성공 (CRUD) | ✓ success | 페이지 전환 시는 생략 가능 |
| API 실패 | ✓ error | inline 에러로 충분하면 생략 |
| 백그라운드 작업 완료 (분석) | ✓ info | |
| 폼 검증 실패 | ✗ | inline 에러 |
| 네트워크 오류 (offline) | ✓ warning + 재시도 | |

위치: 우상단, 동시 최대 3개, 4초 자동 사라짐.

---

## 6. 면접 세션 UI 패턴

### 6.1 Question 표시
- 면접관 영역(좌측)에 large typography
- TTS 재생 시 자동으로 음성 출력 + 자막 동시 표시
- 사용자가 끝까지 듣지 않아도 답변 입력 가능

### 6.2 답변 입력
- 음성 모드: 마이크 버튼 → 녹음 시작, 실시간 STT 자막
- 텍스트 모드: textarea, Enter 제출 (Shift+Enter 줄바꿈) — 채팅형 UI 관례를 따른다
- 답변 제출 후 입력 영역 즉시 disable, "AI가 답변을 분석 중입니다…" 로딩

### 6.3 꼬리질문 도착
- 직전 답변 위에 새 질문 카드 추가 (역순 X — 자연스러운 대화 흐름)
- 부드러운 fade-in (200ms)
- 자동 scroll to 새 질문

### 6.4 세션 종료 트리거
- 사용자 명시 종료: 우상단 종료 버튼 → ConfirmDialog
- 자동 종료: 최대 질문/시간 도달 → "면접이 종료되었습니다" 모달 → 피드백 생성 페이지로 이동

---

## 7. 빈 상태 (EmptyState) 카탈로그

| 위치 | 메시지 | CTA |
|------|--------|-----|
| 이력서 목록 | "아직 업로드한 이력서가 없습니다" | "이력서 업로드" |
| 레포 목록 | "면접에 사용할 레포지토리를 등록해주세요" | "GitHub에서 가져오기" |
| 세션 히스토리 | "첫 모의면접을 시작해보세요" | "면접 시작" |
| 검색 결과 없음 | "'{query}'에 대한 결과가 없습니다" | (없음) |
| 통계 부족 | "최소 2회 이상 면접을 완료하면 추이를 확인할 수 있습니다" | "면접 시작" |

---

## 8. 키보드 단축키

| 단축키 | 동작 | 컨텍스트 |
|--------|------|----------|
| `Esc` | 모달 / 드로어 / 팝오버 닫기 | 전역 |
| `Enter` | 기본 액션 | form, dialog |
| `Enter` | 답변 제출 (`Shift+Enter` 줄바꿈) | 면접 textarea |
| `Cmd/Ctrl + K` | 빠른 검색 (옵션) | 워크스페이스 |
| `Space` | 마이크 toggle (push-to-talk 옵션) | 면접 진행 중 |
| `?` | 단축키 목록 | 전역 |

---

## 9. 모바일 대응

- 면접 세션은 데스크탑 우선. 모바일 진입 시 "데스크탑 환경 권장" 배너 (dismissable)
- 외 페이지: 768px 이하에서 SideNav → Drawer로 전환, TopNav 햄버거 메뉴

---

## 10. 에러 페이지

- **404**: "페이지를 찾을 수 없습니다" + 홈 이동 CTA
- **403**: "접근 권한이 없습니다" + 로그인/홈 이동
- **500**: "일시적인 오류가 발생했습니다" + 새로고침 + 문의 (traceId 표시)
- **오프라인**: 전역 배너 + 자동 재연결 시도

---

## 11. 다국어

- Phase 1: 한국어 only
- 모든 UI 문자열은 `frontend/src/shared/i18n/ko.json`에 키 기반으로 분리 (Phase 2 영어 확장 대비)
- 형식: 평면 키 (`session.list.empty.title`)
