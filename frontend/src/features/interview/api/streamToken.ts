import { apiClient } from '@/shared/api'

// USER 채널(/realtime/stream/me): 분석 상태·피드백 ready 수신용.
export async function fetchUserStreamToken(): Promise<string> {
  const { data } = await apiClient.post<{ streamToken: string }>('/api/auth/stream-token')
  return data.streamToken
}

// SESSION 채널(SSE /realtime/stream/sessions/{id}, WS /realtime/sessions/{id}).
export async function fetchSessionStreamToken(sessionId: number): Promise<string> {
  const { data } = await apiClient.post<{ streamToken: string }>(
    `/api/sessions/${sessionId}/stream-token`,
  )
  return data.streamToken
}
