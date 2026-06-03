import { useEffect } from 'react'
import type { ThreadItem } from '../../model/useLiveInterview'
import { ConversationThread } from './ConversationThread'

// 몰입형 스테이지는 현재 질문에만 집중하므로, 지난 문답 전체는
// 필요할 때만 이 드로어에서 확인한다(채팅 스레드 재사용).
export function TranscriptDrawer({
  items,
  awaitingQuestion,
  onClose,
}: {
  items: ThreadItem[]
  awaitingQuestion: boolean
  onClose: () => void
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="absolute inset-0 z-30 flex">
      <div
        aria-hidden
        className="absolute inset-0 bg-black/30"
        onClick={onClose}
      />
      <aside
        role="dialog"
        aria-label="대화 기록"
        className="relative ml-auto flex h-full w-full max-w-md flex-col bg-bg shadow-xl"
      >
        <header className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="text-h6 text-fg">대화 기록</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="rounded-md px-2 py-1 text-fg-muted transition-colors hover:bg-surface hover:text-fg"
          >
            ✕
          </button>
        </header>
        <div className="min-h-0 flex-1">
          <ConversationThread items={items} awaitingQuestion={awaitingQuestion} />
        </div>
      </aside>
    </div>
  )
}
