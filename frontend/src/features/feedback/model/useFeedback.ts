import { useQuery } from '@tanstack/react-query'
import { isApiError } from '@/shared/api'
import { getFeedback } from '../api/feedbackApi'

export const feedbackKeys = {
  all: ['feedback'] as const,
  detail: (sessionId: number) => [...feedbackKeys.all, sessionId] as const,
}

// 세션 종료 직후엔 피드백이 비동기 생성 중이라 아직 없음(FEEDBACK_NOT_READY/404).
// 이 경우는 에러가 아니라 "생성 중"이므로 일정 횟수까지 polling 한다.
export function isFeedbackPending(err: unknown): boolean {
  return isApiError(err) && (err.code === 'FEEDBACK_NOT_READY' || err.status === 404)
}

export function useFeedback(sessionId: number) {
  return useQuery({
    queryKey: feedbackKeys.detail(sessionId),
    queryFn: () => getFeedback(sessionId),
    retry: (count, err) => isFeedbackPending(err) && count < 40,
    retryDelay: 3000,
  })
}
