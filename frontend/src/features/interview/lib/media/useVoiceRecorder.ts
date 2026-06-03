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

  const recorderRef = useRef<MediaRecorder | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const chunksRef = useRef<Blob[]>([])

  const cleanup = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    recorderRef.current = null
    chunksRef.current = []
  }, [])

  useEffect(() => cleanup, [cleanup])

  const start = useCallback(async (): Promise<boolean> => {
    if (status === 'unsupported' || status === 'recording') return false
    setStatus('requesting')
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      setStatus('denied')
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

  // 녹음 폐기(업로드 안 함).
  const cancel = useCallback(() => {
    const recorder = recorderRef.current
    if (recorder && recorder.state !== 'inactive') {
      recorder.onstop = null
      recorder.stop()
    }
    cleanup()
    setStatus('idle')
  }, [cleanup])

  return { status, start, stop, cancel }
}
