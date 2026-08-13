import type { Message } from '@/domain/session'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { categoryLabel } from '../../lib/categoryLabel'
import { useTtsPlayback } from '../../lib/media/useTtsPlayback'
import { FOLLOWUP_GENERATING_TEXT } from '../../model/streamingBuffer'
import { useTypewriter } from '../../lib/useTypewriter'

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
  streaming = false,
}: {
  message: Message
  autoPlay?: boolean
  streaming?: boolean
}) {
  const label = categoryLabel(message.category)
  const hasMeta = Boolean(label || message.targetEvidence)
  const ttsReady = message.ttsStatus === 'SUCCEEDED'
  const isSentinel = message.content === FOLLOWUP_GENERATING_TEXT
  const shownText = useTypewriter(message.content ?? '', !!streaming && !isSentinel)

  const { playing, toggle, audioNode } = useTtsPlayback({
    sessionId: message.sessionId,
    messageId: message.id,
    enabled: ttsReady,
    autoPlay,
  })

  return (
    <div className="flex justify-start">
      <div className="flex max-w-[80%] flex-col gap-1.5">
        {hasMeta && (
          <div className="flex flex-wrap items-center gap-2">
            {label && <StatusBadge tone="info">{label}</StatusBadge>}
            {message.targetEvidence && (
              <span className="text-caption text-fg-muted">근거: {message.targetEvidence}</span>
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
