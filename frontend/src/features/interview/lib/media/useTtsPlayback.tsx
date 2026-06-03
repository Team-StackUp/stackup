import { useCallback, useEffect, useRef, useState } from 'react'
import { useMessageAudio } from './useMessageAudio'

// 질문 TTS 오디오의 로드·자동재생·토글을 캡슐화한다.
// QuestionBubble(채팅)과 StageQuestion(몰입형 스테이지)이 공유한다.
// audioNode 를 렌더 트리에 그대로 끼워 넣으면 된다.
export function useTtsPlayback({
  sessionId,
  messageId,
  enabled,
  autoPlay = false,
}: {
  sessionId?: number
  messageId?: number
  enabled: boolean
  autoPlay?: boolean
}) {
  const { url, load } = useMessageAudio(sessionId, messageId)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const [playing, setPlaying] = useState(false)
  const wantPlay = useRef(false)
  const autoTried = useRef(false)

  // 최신 질문이면 오디오를 받아 자동재생 시도(브라우저 차단 시 조용히 폴백).
  useEffect(() => {
    if (!autoPlay || !enabled || autoTried.current) return
    autoTried.current = true
    wantPlay.current = true
    void load()
  }, [autoPlay, enabled, load])

  // object URL 이 준비되면 대기 중이던 재생 요청을 반영.
  useEffect(() => {
    if (url && wantPlay.current) {
      wantPlay.current = false
      audioRef.current?.play().catch(() => {})
    }
  }, [url])

  const toggle = useCallback(async () => {
    const el = audioRef.current
    if (!url) {
      wantPlay.current = true
      await load()
      return
    }
    if (!el) return
    if (el.paused) el.play().catch(() => {})
    else el.pause()
  }, [url, load])

  const audioNode =
    enabled && url ? (
      <audio
        ref={audioRef}
        src={url}
        preload="none"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
      />
    ) : null

  return { playing, toggle, audioNode }
}
