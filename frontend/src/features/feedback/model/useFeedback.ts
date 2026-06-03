import { useMutation, useQuery } from '@tanstack/react-query'
import { isApiError } from '@/shared/api'
import { enableShare, getFeedback, getSharedFeedback } from '../api/feedbackApi'

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

// 공유 토큰 발급(버튼 클릭).
export function useShareFeedback(sessionId: number) {
  return useMutation({ mutationFn: () => enableShare(sessionId) })
}

// 공개 페이지: 공유 토큰으로 피드백 조회(비인증, 재시도 없음).
export function useSharedFeedback(token: string) {
  return useQuery({
    queryKey: [...feedbackKeys.all, 'shared', token],
    queryFn: () => getSharedFeedback(token),
    retry: false,
  })
}
