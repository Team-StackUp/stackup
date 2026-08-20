import { useState } from 'react'

const STORAGE_KEY = 'stackup:interview-small-screen-notice-dismissed'
// 아래로는 면접 스테이지(질문 카드 + 컴포저 + 셀프뷰)가 서로 겹치기 시작한다.
const MIN_COMFORTABLE_WIDTH = 768

/**
 * 좁은 화면에서 한 번만 뜨는 안내 (`docs/ui-patterns.md §9`).
 *
 * <p>면접 화면은 데스크탑 우선으로 만들어졌다 — 웹캠 셀프뷰·질문 카드·컴포저가 한 화면에
 * 있어야 하고 마이크 권한도 필요하다. 좁은 화면에서 뭔가 어긋나 보일 때 이유를 모르면
 * 사용자는 제품이 고장난 줄 안다. 막지는 않고(진행은 가능하다) 기대치만 맞춘다.
 */
// 마운트 시점의 폭만 본다. 리사이즈에 반응하지 않는 건 의도다 — 한 번 안내하는 힌트이지
// 창을 줄일 때마다 다시 끼어들 이유가 없다. 이펙트가 아니라 lazy 초기값으로 계산한다
// (이펙트에서 setState 하면 렌더가 한 번 더 돈다 — react-hooks/set-state-in-effect).
function shouldShow(): boolean {
  if (typeof window === 'undefined') return false
  if (window.innerWidth >= MIN_COMFORTABLE_WIDTH) return false
  try {
    return window.localStorage.getItem(STORAGE_KEY) !== '1'
  } catch {
    // 프라이빗 모드 등 저장이 막힌 경우 — 안내는 보여준다.
    return true
  }
}

export function SmallScreenNotice() {
  const [visible, setVisible] = useState(shouldShow)

  if (!visible) return null

  const dismiss = () => {
    setVisible(false)
    try {
      window.localStorage.setItem(STORAGE_KEY, '1')
    } catch {
      /* 무시 */
    }
  }

  return (
    <div
      role="status"
      className="relative z-20 flex items-center justify-between gap-3 bg-info-50 px-4 py-2 text-caption text-info-700"
    >
      <span style={{ wordBreak: 'keep-all' }}>
        화면이 좁아 일부 요소가 겹칠 수 있어요. 데스크탑 환경을 권장합니다.
      </span>
      <button
        type="button"
        onClick={dismiss}
        aria-label="안내 닫기"
        className="shrink-0 rounded-md px-2 py-1 font-medium underline-offset-2 hover:underline"
      >
        닫기
      </button>
    </div>
  )
}
