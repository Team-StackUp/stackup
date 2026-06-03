import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getUserStats, listSessions } from '../api/historyApi'

export const historyKeys = {
  sessions: ['history', 'sessions'] as const,
  stats: ['history', 'stats'] as const,
}

// 무한 스크롤: last 가 아니면 다음 page 번호를 pageParam 으로.
export function useSessions() {
  return useInfiniteQuery({
    queryKey: historyKeys.sessions,
    queryFn: ({ pageParam }) => listSessions(pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : (last.page ?? 0) + 1),
  })
}

export function useUserStats() {
  return useQuery({ queryKey: historyKeys.stats, queryFn: getUserStats })
}
