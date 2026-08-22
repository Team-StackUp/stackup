import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { env } from '@/shared/config/env'

type EventHandler = (data: unknown) => void
type EventHandlers = Record<string, EventHandler>

// 'connecting' = 최초 연결 시도 중(정상 부팅 구간 — 배너 등 경고 UI 로 다루지 말 것),
// 'open' = 수신 중, 'closed' = 끊겨서 백오프 재연결 중(이벤트 유실 가능 구간).
// 한 번 끊긴 뒤의 재시도는 'connecting' 으로 되돌리지 않는다 — 재연결이 성사될 때까지
// 'closed' 를 유지해 소비자가 "지연될 수 있음" 상태를 깜빡임 없이 표시할 수 있게 한다.
export type StreamConnectionStatus = 'connecting' | 'open' | 'closed'

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

// EventSource 는 커스텀 헤더를 못 싣는다 → 인증은 ?access_token= 쿼리(stream token)로 전달. 호스트는 RealTime 서버(REALTIME_BASE_URL).
export function useEventStream({
  path,
  getToken,
  handlers,
  enabled = true,
}: UseEventStreamOptions): StreamConnectionStatus {
  const handlersRef = useRef(handlers)
  const getTokenRef = useRef(getToken)
  const [status, setStatus] = useState<StreamConnectionStatus>('connecting')

  // 렌더 중 ref 쓰기는 금지 → effect 에서 최신 값으로 동기화.
  useLayoutEffect(() => {
    handlersRef.current = handlers
    getTokenRef.current = getToken
  })

  useEffect(() => {
    if (!enabled || !path) return

    // 초기 state 가 'connecting' 이라 마운트 시 리셋이 필요 없다. path/enabled 는 현재
    // 사용처에서 상수라 재연결로 인한 상태 리셋 경로는 두지 않는다(effect 내 동기 setState 금지).
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
        // StrictMode 재마운트에서 버려진 effect 의 늦은 reject 가 새 effect 의 상태를
        // 덮어쓰지 않도록, 다른 분기와 동일하게 cancelled 를 먼저 확인한다.
        if (cancelled) return
        // 첫 시도 실패도 '유실 가능 구간'이므로 closed 로 승격한다.
        setStatus('closed')
        scheduleReconnect()
        return
      }
      if (cancelled) return

      const query = token ? `?access_token=${encodeURIComponent(token)}` : ''
      const es = new EventSource(`${env.REALTIME_BASE_URL}${path}${query}`, {
        withCredentials: true,
      })
      source = es

      es.onopen = () => {
        attempt = 0
        setStatus('open')
      }
      es.onerror = () => {
        es.close()
        if (source === es) source = null
        if (!cancelled) {
          setStatus('closed')
          scheduleReconnect()
        }
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

  return status
}
