import type { Message } from '@/domain/session'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { categoryLabel } from '../../lib/categoryLabel'
import { useTtsPlayback } from '../../lib/media/useTtsPlayback'
import { FOLLOWUP_GENERATING_TEXT } from '../../model/streamingBuffer'
import { useSetQuestionBookmark } from '../../model/useBookmarks'

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 20 20"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="m10 2.8 2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5L2.8 8.1l5-.7z" />
    </svg>
  )
}

function PlayIcon({ playing }: { playing: boolean }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      {playing ? <path d="M8 6h3v12H8zM13 6h3v12h-3z" /> : <path d="M8 5v14l11-7z" />}
    </svg>
  )
}

export function QuestionBubble({
  message,
  autoPlay = false,
  bookmarkable = false,
}: {
  message: Message
  autoPlay?: boolean
  /** 오답노트 표시 버튼 노출. 라이브 중엔 끄고(집중 방해) 종료 세션 기록에서만 켠다. */
  bookmarkable?: boolean
}) {
  const label = categoryLabel(message.category)
  const bookmark = useSetQuestionBookmark(message.sessionId ?? 0)
  const hasMeta = Boolean(label || message.targetEvidence)
  const ttsReady = message.ttsStatus === 'SUCCEEDED'
  const isSentinel = message.content === FOLLOWUP_GENERATING_TEXT
  // 델타가 이미 토큰 단위로 도착하므로 재애니메이션 없이 그대로 표시한다(실스트림 = 애니메이션).
  const shownText = message.content ?? ''

  const { playing, toggle, audioNode } = useTtsPlayback({
    sessionId: message.sessionId,
    messageId: message.id,
    enabled: ttsReady,
    autoPlay,
  })

  return (
    <div className="flex justify-start">
      <div className="flex max-w-[80%] flex-col gap-1.5">
        {(hasMeta || bookmarkable) && (
          <div className="flex flex-wrap items-center gap-2">
            {label && <StatusBadge tone="info">{label}</StatusBadge>}
            {message.targetEvidence && (
              <span className="text-caption text-fg-muted">근거: {message.targetEvidence}</span>
            )}
            {bookmarkable && message.id != null && (
              <button
                type="button"
                disabled={bookmark.isPending}
                aria-pressed={message.bookmarked ?? false}
                aria-label={
                  message.bookmarked ? '오답노트에서 빼기' : '오답노트에 담기'
                }
                onClick={() =>
                  bookmark.mutate({
                    messageId: message.id as number,
                    bookmarked: !(message.bookmarked ?? false),
                  })
                }
                className={[
                  'rounded-md px-1.5 py-0.5 text-caption transition-colors duration-fast',
                  message.bookmarked
                    ? 'text-primary-fg hover:bg-surface'
                    : 'text-fg-subtle hover:bg-surface hover:text-fg-muted',
                  'disabled:opacity-40',
                ].join(' ')}
              >
                <StarIcon filled={message.bookmarked ?? false} />
              </button>
            )}
          </div>
        )}
        <div className="rounded-xl rounded-tl-sm border border-border bg-surface-raised px-4 py-3 text-body text-fg">
          {isSentinel ? (
            <span className="inline-flex gap-1 text-fg-muted" aria-label="질문 생성 중">
              <span className="animate-pulse">●</span>
              <span className="animate-pulse [animation-delay:150ms]">●</span>
              <span className="animate-pulse [animation-delay:300ms]">●</span>
            </span>
          ) : (
            <p className="whitespace-pre-wrap">{shownText}</p>
          )}
          {ttsReady && (
            <div className="mt-2 flex items-center gap-2 border-t border-border pt-2">
              <button
                type="button"
                onClick={toggle}
                aria-label={playing ? '음성 일시정지' : '질문 음성 재생'}
                className="inline-flex items-center gap-1.5 rounded-pill px-2.5 py-1 text-caption text-fg-muted transition-colors hover:bg-surface hover:text-fg"
              >
                <PlayIcon playing={playing} />
                {playing ? '일시정지' : '음성 듣기'}
              </button>
              {audioNode}
            </div>
          )}
        </div>
        {/* expected_signal 은 종료된 세션에서만 내려옴(피드백 학습용). 라이브엔 비노출. */}
        {message.expectedSignal && (
          <div className="rounded-md border border-dashed border-border bg-surface px-3 py-2 text-caption text-fg-muted">
            <span className="font-medium text-fg">이 질문이 본 핵심</span> ·{' '}
            {message.expectedSignal}
          </div>
        )}
      </div>
    </div>
  )
}
