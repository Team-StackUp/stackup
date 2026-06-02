import { useQuery } from '@tanstack/react-query'
import { getSession } from '../api/sessionApi'

export const sessionKeys = {
  all: ['sessions'] as const,
  detail: (id: number) => [...sessionKeys.all, 'detail', id] as const,
}

export function useSession(sessionId: number) {
  return useQuery({
    queryKey: sessionKeys.detail(sessionId),
    queryFn: () => getSession(sessionId),
  })
}
