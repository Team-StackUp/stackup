import { useCallback, useEffect, useRef, useState } from 'react'

export type RecorderStatus = 'idle' | 'requesting' | 'recording' | 'denied' | 'unsupported'

// MediaRecorder 가 만들 수 있는 후보. 백엔드 허용 목록(webm/ogg/mpeg/mp4/wav)과 교집합.
const PREFERRED_MIME = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/ogg']

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder === 'undefined') return undefined
  return PREFERRED_MIME.find((t) => MediaRecorder.isTypeSupported(t))
}

// 라이브 면접 음성 답변 녹음. start → 마이크 권한 요청 + 녹음, stop → 오디오 Blob 반환.
// 권한 거부/미지원 시 status 로 알려서 호출부가 텍스트 입력으로 fallback 한다.
export function useVoiceRecorder() {
  const [status, setStatus] = useState<RecorderStatus>(() =>
    typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia
      ? 'unsupported'
      : 'idle',
  )

  // 권한 요청은 사용자가 프롬프트를 방치하면 영원히 pending 이다. 취소 시 이 값을 올려
  // 뒤늦게 도착한 스트림을 무효화한다 — 안 그러면 취소 후에도 녹음이 시작되고 마이크가 켜진다.
  const requestIdRef = useRef(0)
  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])
  // 녹음 중 실시간 레벨 미터(MicLevelMeter)가 구독할 수 있도록 스트림을 상태로도 노출.
  const [stream, setStream] = useState<MediaStream | null>(null)

  const cleanup = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    recorderRef.current = null
    chunksRef.current = []
    setStream(null)
  }, [])

  useEffect(() => cleanup, [cleanup])

  const start = useCallback(async (): Promise<boolean> => {
    if (status === 'unsupported' || status === 'recording') return false
    const requestId = ++requestIdRef.current
    setStatus('requesting')
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      // 취소 뒤 늦게 거부가 오면 이미 idle 인 상태를 denied 로 되돌리면 안 된다.
      if (requestIdRef.current === requestId) setStatus('denied')
      return false
    }
    // 대기 중에 취소했으면 방금 열린 트랙을 바로 닫는다(마이크 표시등이 계속 켜진 채 남는다).
    if (requestIdRef.current !== requestId) {
      stream.getTracks().forEach((t) => t.stop())
      return false
    }
    const mimeType = pickMimeType()
    const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
    chunksRef.current = []
    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunksRef.current.push(e.data)
    }
    recorder.start()
    streamRef.current = stream
    recorderRef.current = recorder
    setStream(stream)
    setStatus('recording')
    return true
  }, [status])

  // 녹음 중지 후 Blob 반환. 녹음 중이 아니면 null.
  const stop = useCallback((): Promise<Blob | null> => {
    const recorder = recorderRef.current
    if (!recorder || recorder.state === 'inactive') {
      cleanup()
      setStatus('idle')
      return Promise.resolve(null)
    }
    return new Promise((resolve) => {
      recorder.onstop = () => {
        const type = recorder.mimeType || chunksRef.current[0]?.type || 'audio/webm'
        const blob = new Blob(chunksRef.current, { type })
        cleanup()
        setStatus('idle')
        resolve(blob.size > 0 ? blob : null)
      }
      recorder.stop()
    })
  }, [cleanup])

  // 녹음 폐기(업로드 안 함). 아직 권한 대기 중이어도 호출할 수 있다 — 그 경우 진행 중인
  // getUserMedia 를 무효화해 사용자가 텍스트 입력으로 돌아갈 수 있게 한다.
  const cancel = useCallback(() => {
    requestIdRef.current += 1
    const recorder = recorderRef.current
    if (recorder && recorder.state !== 'inactive') {
      recorder.onstop = null
      recorder.stop()
    }
    cleanup()
    setStatus('idle')
  }, [cleanup])

  return { status, stream, start, stop, cancel }
}
