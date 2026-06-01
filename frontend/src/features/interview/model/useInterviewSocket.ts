import { useEffect, useRef } from 'react'
import { env } from '@/shared/config/env'
import { fetchSessionStreamToken } from '@/features/interview/api/streamToken'

type InterviewEvent = { id: string; event: string; data: unknown }
type Options = {
  sessionId: number | null
  onEvent: (event: InterviewEvent) => void
  enabled?: boolean
}

// 라이브 텍스트 면접 WebSocket. 서버→클라 질문/꼬리질문 수신 + 클라→서버 답변 송신.
export function useInterviewSocket({ sessionId, onEvent, enabled = true }: Options) {
  const socketRef = useRef<WebSocket | null>(null)
  const onEventRef = useRef(onEvent)
  onEventRef.current = onEvent

  useEffect(() => {
    if (!enabled || sessionId == null) return
    let cancelled = false
    let ws: WebSocket | null = null

    const connect = async () => {
      const token = await fetchSessionStreamToken(sessionId)
      if (cancelled) return
      const base = env.REALTIME_BASE_URL.replace(/^http/, 'ws')
      ws = new WebSocket(`${base}/realtime/sessions/${sessionId}?access_token=${encodeURIComponent(token)}`)
      socketRef.current = ws
      ws.onmessage = (e) => {
        try {
          onEventRef.current(JSON.parse(e.data) as InterviewEvent)
        } catch {
          // ignore non-JSON
        }
      }
    }
    void connect()

    return () => {
      cancelled = true
      ws?.close()
      socketRef.current = null
    }
  }, [sessionId, enabled])

  const submitAnswer = (content: string, idempotencyKey?: string) => {
    socketRef.current?.send(JSON.stringify({ type: 'answer', content, idempotencyKey }))
  }

  return { submitAnswer }
}
