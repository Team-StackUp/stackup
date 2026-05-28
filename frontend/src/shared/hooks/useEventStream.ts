import { useEffect, useLayoutEffect, useRef } from 'react'
import { env } from '@/shared/config/env'

type EventHandler = (data: unknown) => void
type EventHandlers = Record<string, EventHandler>

type UseEventStreamOptions = {
  /** SSE_BASE_URL 에 붙일 경로. null 이면 연결하지 않음. */
  path: string | null
  /** 매 (재)연결 시점에 stream token 을 새로 발급받는다. */
  getToken: () => Promise<string | null>
  /** SSE `event:` 이름 → 핸들러. data 는 JSON 파싱되어 전달됨(파싱 실패 시 raw 문자열). */
  handlers: EventHandlers
  enabled?: boolean
}

const BASE_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 30_000

// EventSource 는 커스텀 헤더를 못 싣는다 → 인증은 ?token= 쿼리(stream token)로 전달.
export function useEventStream({
  path,
  getToken,
  handlers,
  enabled = true,
}: UseEventStreamOptions): void {
  const handlersRef = useRef(handlers)
  const getTokenRef = useRef(getToken)

  // 렌더 중 ref 쓰기는 금지 → effect 에서 최신 값으로 동기화.
  useLayoutEffect(() => {
    handlersRef.current = handlers
    getTokenRef.current = getToken
  })

  useEffect(() => {
    if (!enabled || !path) return

    let source: EventSource | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let attempt = 0
    let cancelled = false

    const scheduleReconnect = () => {
      const delay = Math.min(BASE_BACKOFF_MS * 2 ** attempt, MAX_BACKOFF_MS)
      attempt += 1
      reconnectTimer = setTimeout(() => {
        if (!cancelled) void connect()
      }, delay)
    }

    const connect = async () => {
      if (cancelled) return
      let token: string | null = null
      try {
        token = await getTokenRef.current()
      } catch {
        scheduleReconnect()
        return
      }
      if (cancelled) return

      const query = token ? `?token=${encodeURIComponent(token)}` : ''
      const es = new EventSource(`${env.SSE_BASE_URL}${path}${query}`, {
        withCredentials: true,
      })
      source = es

      es.onopen = () => {
        attempt = 0
      }
      es.onerror = () => {
        es.close()
        if (source === es) source = null
        if (!cancelled) scheduleReconnect()
      }

      for (const name of Object.keys(handlersRef.current)) {
        es.addEventListener(name, (event) => {
          const raw = (event as MessageEvent<string>).data
          let parsed: unknown = raw
          try {
            parsed = JSON.parse(raw)
          } catch {
            // keep-alive 등 비 JSON payload — raw 전달
          }
          handlersRef.current[name]?.(parsed)
        })
      }
    }

    void connect()

    return () => {
      cancelled = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      source?.close()
      source = null
    }
  }, [path, enabled])
}
