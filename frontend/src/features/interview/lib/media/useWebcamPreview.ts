import { useCallback, useEffect, useRef, useState } from 'react'

export type WebcamState = 'idle' | 'requesting' | 'live' | 'denied' | 'unsupported'

// 라이브 면접 중 본인 카메라 미리보기(PiP) 전용 훅.
// 실제 스트림 전송·분석은 범위 밖 — 로컬 미리보기만 담당한다.
export function useWebcamPreview() {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [state, setState] = useState<WebcamState>('idle')

  // 권한 프롬프트는 사용자가 방치하면 오래 pending 이다. 그 사이 stop() 이나 언마운트가
  // 일어나면 이 값을 올려 뒤늦게 도착한 스트림을 무효화한다 — 안 그러면 소유자 없는
  // 트랙이 살아남아 카메라 LED 가 계속 켜진 채로 남는다.
  const requestIdRef = useRef(0)

  const stop = useCallback(() => {
    requestIdRef.current += 1
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
    const requestId = ++requestIdRef.current
    setState('requesting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true })
      // 대기 중에 stop()/언마운트로 무효화됐으면 방금 열린 트랙을 바로 닫는다.
      if (requestIdRef.current !== requestId) {
        stream.getTracks().forEach((t) => t.stop())
        return
      }
      streamRef.current = stream
      setState('live')
    } catch {
      // 무효화 뒤 늦게 거부가 오면 상태를 되돌리지 않는다.
      if (requestIdRef.current === requestId) setState('denied')
    }
  }, [])

  // 스트림 준비 후 비디오 엘리먼트가 마운트되는 경우까지 커버해 srcObject 바인딩.
  useEffect(() => {
    if (state === 'live' && videoRef.current && streamRef.current) {
      videoRef.current.srcObject = streamRef.current
    }
  }, [state])

  // 언마운트 시 트랙 정리 (카메라 LED 가 계속 켜지는 것 방지 — 필수).
  // requestId 를 올려 아직 pending 인 getUserMedia 도 무효화한다 (resolve 가 언마운트
  // 뒤에 와도 start() 가 그 트랙을 바로 닫는다).
  useEffect(
    () => () => {
      requestIdRef.current += 1
      streamRef.current?.getTracks().forEach((t) => t.stop())
    },
    [],
  )

  return { videoRef, state, start, stop }
}
