import { useCallback, useEffect, useRef, useState } from 'react'

export type WebcamState = 'idle' | 'requesting' | 'live' | 'denied' | 'unsupported'

// 라이브 면접 중 본인 카메라 미리보기(PiP) 전용 훅.
// 실제 스트림 전송·분석은 범위 밖 — 로컬 미리보기만 담당한다.
export function useWebcamPreview() {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [state, setState] = useState<WebcamState>('idle')

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
    setState('idle')
  }, [])

  const start = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setState('unsupported')
      return
    }
    setState('requesting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true })
      streamRef.current = stream
      setState('live')
    } catch {
      setState('denied')
    }
  }, [])

  // 스트림 준비 후 비디오 엘리먼트가 마운트되는 경우까지 커버해 srcObject 바인딩.
  useEffect(() => {
    if (state === 'live' && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current
    }
  }, [state])

  // 언마운트 시 트랙 정리 (카메라 LED 가 계속 켜지는 것 방지 — 필수).
  useEffect(
    () => () => {
      streamRef.current?.getTracks().forEach((t) => t.stop())
    },
    [],
  )

  return { videoRef, state, start, stop }
}
