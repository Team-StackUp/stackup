import { useState } from 'react'
import type { Message } from '@/domain/session'
import { categoryLabel } from '../../lib/categoryLabel'
import { useTtsPlayback } from '../../lib/media/useTtsPlayback'
import { useTypewriter } from '../../lib/useTypewriter'
import type { DeliveryMode } from '../../model/useDeliveryMode'

function PlayIcon({ playing }: { playing: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      {playing ? <path d="M8 6h3v12H8zM13 6h3v12h-3z" /> : <path d="M8 5v14l11-7z" />}
    </svg>
  )
}

// 음성 재생/대기 상태를 나타내는 이퀄라이저. playing=false 면 정지된 막대.
function VoiceWave({ playing }: { playing: boolean }) {
  const bars = [0, 1, 2, 3, 4]
  return (
    <span className="flex h-8 items-end gap-1" aria-hidden>
      {bars.map((i) => (
        <span
          key={i}
          className={`w-1.5 rounded-full bg-sage-700 ${playing ? 'anim-eq-bar' : ''}`}
          style={{ height: '100%', animationDelay: `${i * 120}ms` }}
        />
      ))}
    </span>
  )
}

// 면접관이 지금 막 던진 한 질문에만 집중시키는 카드.
export function StageQuestion({
  question,
  segmented = false,
  speaking = false,
  streaming = false,
  mode = 'text',
}: {
  question: Message
  segmented?: boolean
  speaking?: boolean
  streaming?: boolean
  mode?: DeliveryMode
}) {
  const label = categoryLabel(question.category)
  const ttsStatus = question.ttsStatus
  const ttsReady = ttsStatus === 'SUCCEEDED'
  const ttsPending = ttsStatus === 'PENDING'
  const ttsFailed = ttsStatus === 'FAILED'
  const voiceMode = mode === 'voice'
  const shownText = useTypewriter(question.content ?? '', !!streaming)

  // 음성 모드여도 TTS 가 실패했으면 텍스트로 폴백한다.
  const listenOnly = voiceMode && !ttsFailed
  const [revealText, setRevealText] = useState(false)
  const showText = !listenOnly || revealText

  const { playing, toggle, audioNode } = useTtsPlayback({
    sessionId: question.sessionId,
    messageId: question.id,
    // 텍스트 모드에서는 절대 자동재생하지 않아 읽는 도중 끼어들지 않게 한다.
    enabled: ttsReady,
    autoPlay: voiceMode && !segmented,
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
          {label && <span className="text-caption text-fg-muted">{label}</span>}
        </div>
      </div>

      {showText && (
        <p className="mt-5 whitespace-pre-wrap text-[22px] font-medium leading-relaxed text-fg sm:text-[26px]">
          {shownText}
        </p>
      )}

      {/* 음성 모드: 듣기에 집중하도록 텍스트를 숨기고 재생 상태를 보여준다. */}
      {listenOnly && (
        <div className="mt-6 flex flex-col items-center gap-4 py-4">
          {ttsReady ? (
            <>
              {/* 전체 파일 준비 완료 — 수동 재생/일시정지. 끝나지 않은 세그먼트가
                  계속 흐르는 동안에도 파형은 유지한다(playing || speaking). */}
              <VoiceWave playing={playing || speaking} />
              <button
                type="button"
                onClick={toggle}
                aria-label={playing ? '음성 일시정지' : '질문 다시 듣기'}
                className="inline-flex items-center gap-1.5 rounded-pill border border-border bg-white/70 px-4 py-2 text-caption font-medium text-fg transition-colors hover:bg-white"
              >
                <PlayIcon playing={playing} />
                {playing ? '재생 중 · 일시정지' : '다시 듣기'}
              </button>
            </>
          ) : segmented ? (
            <>
              {/* 라이브 세그먼트가 도착·재생 중 — 실제 재생 상태로 파형을 움직인다. */}
              <VoiceWave playing={speaking} />
              <p className="text-body font-medium text-fg-muted">질문을 들려드리고 있어요…</p>
            </>
          ) : (
            <p className="flex items-center gap-2 text-body font-medium text-fg-muted">
              <span className="flex gap-1" aria-hidden>
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="h-2 w-2 animate-pulse rounded-full bg-sage-700/70"
                    style={{ animationDelay: `${i * 150}ms` }}
                  />
                ))}
              </span>
              음성을 준비하고 있어요…
            </p>
          )}
          <button
            type="button"
            onClick={() => setRevealText((v) => !v)}
            className="text-caption text-fg-muted underline-offset-2 transition-colors hover:text-fg hover:underline"
          >
            {revealText ? '텍스트 숨기기' : '텍스트로 보기'}
          </button>
          {audioNode}
        </div>
      )}

      {/* 텍스트 모드: 음성은 끼어들지 않고, 상태/수동 재생만 제공한다. */}
      {!listenOnly && (ttsReady || ttsPending) && (
        <div className="mt-6 flex items-center gap-2">
          {ttsReady ? (
            <button
              type="button"
              onClick={toggle}
              aria-label={playing ? '음성 일시정지' : '질문 음성 재생'}
              className="inline-flex items-center gap-1.5 rounded-pill border border-border bg-white/70 px-3 py-1.5 text-caption font-medium text-fg transition-colors hover:bg-white"
            >
              <PlayIcon playing={playing} />
              {playing ? '일시정지' : '질문 듣기'}
            </button>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-pill border border-border bg-white/40 px-3 py-1.5 text-caption text-fg-muted">
              <span className="flex gap-1" aria-hidden>
                {[0, 1, 2].map((i) => (
                  <span
                    key={i}
                    className="h-1.5 w-1.5 animate-pulse rounded-full bg-sage-700/60"
                    style={{ animationDelay: `${i * 150}ms` }}
                  />
                ))}
              </span>
              음성 준비 중
            </span>
          )}
          {audioNode}
        </div>
      )}
    </div>
  )
}
