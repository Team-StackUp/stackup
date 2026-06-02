import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']
export type Feedback = S['FeedbackResponse']

export async function getFeedback(sessionId: number): Promise<Feedback> {
  return (await apiClient.get<Feedback>(`/api/sessions/${sessionId}/feedback`)).data
}
