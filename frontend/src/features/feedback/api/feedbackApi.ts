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

// 공유 해제(멱등). 기존 공유 링크는 즉시 404.
export async function disableShare(sessionId: number): Promise<void> {
  await apiClient.delete(`/api/sessions/${sessionId}/feedback/share`)
}

// 피드백 재생성 요청 — 발행 유실·AI 실패로 피드백이 오지 않을 때의 복구 경로. 202 accepted.
export async function regenerateFeedback(sessionId: number): Promise<void> {
  await apiClient.post(`/api/sessions/${sessionId}/feedback/regenerate`)
}

// 공개 토큰으로 피드백 조회(비인증).
export async function getSharedFeedback(token: string): Promise<Feedback> {
  return (await apiClient.get<Feedback>(`/api/public/feedbacks/${token}`)).data
}
