import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']
export type Session = S['SessionResponse']
export type SessionPage = S['PageResponseSessionResponse']
export type UserStats = S['UserStatsResponse']

// Spring 이 ?page=&size= 를 바인딩하므로 쿼리 파라미터로 직접 전달.
export async function listSessions(page = 0, size = 20): Promise<SessionPage> {
  return (
    await apiClient.get<SessionPage>('/api/sessions', { params: { page, size } })
  ).data
}

export async function getUserStats(): Promise<UserStats> {
  return (await apiClient.get<UserStats>('/api/users/me/stats')).data
}
