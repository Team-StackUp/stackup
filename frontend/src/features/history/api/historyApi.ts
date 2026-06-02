import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']
export type Session = S['SessionResponse']
export type UserStats = S['UserStatsResponse']

export async function listSessions(): Promise<Session[]> {
  return (await apiClient.get<Session[]>('/api/sessions')).data
}

export async function getUserStats(): Promise<UserStats> {
  return (await apiClient.get<UserStats>('/api/users/me/stats')).data
}
