import { useCallback, useEffect, useRef } from 'react'
import { env } from '@/shared/config/env'
import { fetchSessionStreamToken } from '@/features/interview/api/streamToken'

type InterviewEvent = { id: string; event: string; data: unknown }
type ConnectionStatus = 'connecting' | 'open' | 'closed'
type Options = {
  sessionId: number | null
  onEvent: (event: InterviewEvent) => void
  /** 연결 상태 변화 알림 (재연결 UX 표시용). */
  onStatusChange?: (status: ConnectionStatus) => void
  enabled?: boolean
}

const BASE_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 30_000

// 라이브 텍스트 면접 WebSocket. 서버→클라 질문/꼬리질문 수신 + 클라→서버 답변 송신.
// 끊기면 지수 백오프로 자동 재연결한다(매 시도마다 stream token 재발급).
export function useInterviewSocket({
  sessionId,
  onEvent,
  onStatusChange,
  enabled = true,
}: Options) {
  const socketRef = useRef<WebSocket | null>(null)
  const onEventRef = useRef(onEvent)
  const onStatusRef = useRef(onStatusChange)
  // 콜백은 매 렌더 새로 만들어지므로 ref 로 최신 것을 넘긴다(연결 이펙트를 재실행시키지 않기 위해).
  // 렌더 중 대입은 react-hooks/refs 위반이라 커밋 후 이펙트에서 갱신하고, 아래 연결 이펙트보다
  // 먼저 선언해 같은 커밋에서 ref 가 항상 먼저 최신화되게 한다.
  useEffect(() => {
    onEventRef.current = onEvent
    onStatusRef.current = onStatusChange
  }, [onEvent, onStatusChange])

  useEffect(() => {
    if (!enabled || sessionId == null) return

    let cancelled = false
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let attempt = 0

    const scheduleReconnect = () => {
      if (cancelled) return
      const delay = Math.min(BASE_BACKOFF_MS * 2 ** attempt, MAX_BACKOFF_MS)
      attempt += 1
      reconnectTimer = setTimeout(() => {
        if (!cancelled) void connect()
      }, delay)
    }

    const connect = async () => {
      if (cancelled) return
      onStatusRef.current?.('connecting')
      let token: string
      try {
        token = await fetchSessionStreamToken(sessionId)
      } catch {
        scheduleReconnect()
        return
      }
      if (cancelled) return

      const base = env.REALTIME_BASE_URL.replace(/^http/, 'ws')
      const ws = new WebSocket(
        `${base}/realtime/sessions/${sessionId}?access_token=${encodeURIComponent(token)}`,
      )
      socketRef.current = ws

      ws.onopen = () => {
        attempt = 0
        onStatusRef.current?.('open')
      }
      ws.onmessage = (e) => {
        try {
          onEventRef.current(JSON.parse(e.data) as InterviewEvent)
        } catch {
          // ignore non-JSON
        }
      }
      ws.onclose = () => {
        if (socketRef.current === ws) socketRef.current = null
        onStatusRef.current?.('closed')
        scheduleReconnect()
      }
      // onerror is followed by onclose; let onclose drive the reconnect to avoid double-scheduling.
      ws.onerror = () => ws.close()
    }

    void connect()

    return () => {
      cancelled = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      socketRef.current?.close()
      socketRef.current = null
    }
  }, [sessionId, enabled])

  const submitAnswer = useCallback((content: string, idempotencyKey?: string) => {
    const ws = socketRef.current
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'answer', content, idempotencyKey }))
    }
  }, [])

  return { submitAnswer }
}
