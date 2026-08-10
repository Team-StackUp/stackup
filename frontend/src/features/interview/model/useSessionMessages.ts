import { useQuery } from '@tanstack/react-query'
import { listMessages } from '../api/messageApi'

export const messageKeys = {
  all: ['messages'] as const,
  list: (sessionId: number) => [...messageKeys.all, sessionId] as const,
}

export function useSessionMessages(sessionId: number, enabled = true, pollMs: number | false = false) {
  return useQuery({
    queryKey: messageKeys.list(sessionId),
    queryFn: () => listMessages(sessionId),
    enabled,
    // WS 단절 구간의 안전망. 호출부(useLiveInterview)가 연결 상태를 알기에
    // '연결이 정상이 아닐 때만' 폴링을 켠다 — 정상 경로에선 서버 부하 없음.
    refetchInterval: pollMs,
  })
}
