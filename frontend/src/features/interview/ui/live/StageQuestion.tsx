import type { Message } from '@/domain/session'
import { categoryLabel } from '../../lib/categoryLabel'
import { useTtsPlayback } from '../../lib/media/useTtsPlayback'
import { useTypewriter } from '../../lib/useTypewriter'

function PlayIcon({ playing }: { playing: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      {playing ? <path d="M8 6h3v12H8zM13 6h3v12h-3z" /> : <path d="M8 5v14l11-7z" />}
    </svg>
  )
}

// 면접관이 지금 막 던진 한 질문에만 집중시키는 카드.
export function StageQuestion({ question, segmented = false, streaming = false }: { question: Message; segmented?: boolean; streaming?: boolean }) {
  const label = categoryLabel(question.category)
  const ttsReady = question.ttsStatus === 'SUCCEEDED'
  const shownText = useTypewriter(question.content ?? '', !!streaming)

  const { playing, toggle, audioNode } = useTtsPlayback({
    sessionId: question.sessionId,
    messageId: question.id,
    enabled: ttsReady,
    autoPlay: !segmented,
  })

  return (
    <div className="w-full max-w-2xl rounded-2xl border border-white/50 bg-white/70 px-6 py-7 shadow-lg backdrop-blur-md sm:px-9 sm:py-10">
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden
          className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-sage-800 text-[11px] font-semibold uppercase tracking-wide text-white"
        >
          AI
        </span>
        <div className="flex flex-col">
          <span className="text-caption font-medium text-fg">면접관</span>
          {label && (
            <span className="text-caption text-fg-muted">{label}</span>
          )}
        </div>
      </div>

      <p className="mt-5 whitespace-pre-wrap text-[22px] font-medium leading-relaxed text-fg sm:text-[26px]">
        {shownText}
      </p>

      {ttsReady && (
        <div className="mt-6 flex items-center gap-2">
          <button
            type="button"
            onClick={toggle}
            aria-label={playing ? '음성 일시정지' : '질문 음성 재생'}
            className="inline-flex items-center gap-1.5 rounded-pill border border-border bg-white/70 px-3 py-1.5 text-caption font-medium text-fg transition-colors hover:bg-white"
          >
            <PlayIcon playing={playing} />
            {playing ? '일시정지' : '질문 다시 듣기'}
          </button>
          {audioNode}
        </div>
      )}
    </div>
  )
}
