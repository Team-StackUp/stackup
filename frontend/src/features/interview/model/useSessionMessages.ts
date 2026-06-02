import { useQuery } from '@tanstack/react-query'
import { listMessages } from '../api/messageApi'

export const messageKeys = {
  all: ['messages'] as const,
  list: (sessionId: number) => [...messageKeys.all, sessionId] as const,
}

export function useSessionMessages(sessionId: number, enabled = true) {
  return useQuery({
    queryKey: messageKeys.list(sessionId),
    queryFn: () => listMessages(sessionId),
    enabled,
  })
}
