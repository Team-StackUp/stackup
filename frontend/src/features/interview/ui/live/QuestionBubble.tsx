import { useEffect, useRef, useState } from 'react'
import type { Message } from '@/domain/session'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { useMessageAudio } from '../../lib/media/useMessageAudio'

const CATEGORY_LABEL: Record<string, string> = {
  CS_FUNDAMENTAL: 'CS 기초',
  PROJECT_DEEP_DIVE: '프로젝트 심화',
  TECH_CHOICE: '기술 선택',
  BEHAVIORAL: '인성·행동',
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
}: {
  message: Message
  autoPlay?: boolean
}) {
  const categoryLabel = message.category
    ? (CATEGORY_LABEL[message.category] ?? message.category)
    : null
  const hasMeta = Boolean(categoryLabel || message.targetEvidence)
  const ttsReady = message.ttsStatus === 'SUCCEEDED'

  const { url, load } = useMessageAudio(message.sessionId, message.id)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [playing, setPlaying] = useState(false)
  const wantPlay = useRef(false)
  const autoTried = useRef(false)

  // 최신 질문이면 오디오를 받아 자동재생 시도(차단 시 조용히 폴백).
  useEffect(() => {
    if (!autoPlay || !ttsReady || autoTried.current) return
    autoTried.current = true
    wantPlay.current = true
    void load()
  }, [autoPlay, ttsReady, load])

  // object URL 이 준비되면 재생 요청을 반영.
  useEffect(() => {
    if (url && wantPlay.current) {
      wantPlay.current = false
      audioRef.current?.play().catch(() => {})
    }
  }, [url])

  const toggle = async () => {
    const el = audioRef.current
    if (!url) {
      wantPlay.current = true
      await load()
      return
    }
    if (!el) return
    if (el.paused) el.play().catch(() => {})
    else el.pause()
  }

  return (
    <div className="flex justify-start">
      <div className="flex max-w-[80%] flex-col gap-1.5">
        {hasMeta && (
          <div className="flex flex-wrap items-center gap-2">
            {categoryLabel && <StatusBadge tone="info">{categoryLabel}</StatusBadge>}
            {message.targetEvidence && (
              <span className="text-caption text-fg-muted">근거: {message.targetEvidence}</span>
            )}
          </div>
        )}
        <div className="rounded-lg rounded-tl-sm bg-surface-raised px-4 py-3 text-body text-fg shadow-sm">
          <p className="whitespace-pre-wrap">{message.content}</p>
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
              {url && (
                <audio
                  ref={audioRef}
                  src={url}
                  preload="none"
                  onPlay={() => setPlaying(true)}
                  onPause={() => setPlaying(false)}
                  onEnded={() => setPlaying(false)}
                />
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
