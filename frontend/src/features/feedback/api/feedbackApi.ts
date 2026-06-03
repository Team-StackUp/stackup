import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']
export type Feedback = S['FeedbackResponse']

export async function getFeedback(sessionId: number): Promise<Feedback> {
  return (await apiClient.get<Feedback>(`/api/sessions/${sessionId}/feedback`)).data
}

// 공유 토큰 발급(멱등). 토큰 문자열 반환.
export async function enableShare(sessionId: number): Promise<string> {
  const res = await apiClient.post<{ shareToken?: string }>(
    `/api/sessions/${sessionId}/feedback/share`,
  )
  return res.data.shareToken ?? ''
}

// 공개 토큰으로 피드백 조회(비인증).
export async function getSharedFeedback(token: string): Promise<Feedback> {
  return (await apiClient.get<Feedback>(`/api/public/feedbacks/${token}`)).data
}
