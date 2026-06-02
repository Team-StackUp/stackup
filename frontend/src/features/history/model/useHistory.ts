import { useQuery } from '@tanstack/react-query'
import { getUserStats, listSessions } from '../api/historyApi'

export const historyKeys = {
  sessions: ['history', 'sessions'] as const,
  stats: ['history', 'stats'] as const,
}

export function useSessions() {
  return useQuery({ queryKey: historyKeys.sessions, queryFn: listSessions })
}

export function useUserStats() {
  return useQuery({ queryKey: historyKeys.stats, queryFn: getUserStats })
}
