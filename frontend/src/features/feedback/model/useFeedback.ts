import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isApiError } from '@/shared/api'
import { toast } from '@/shared/ui'
import {
  disableShare,
  enableShare,
  getFeedback,
  getSharedFeedback,
  regenerateFeedback,
} from '../api/feedbackApi'

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

// 공유 토큰 발급(버튼 클릭). 성공 시 상세 캐시 무효화 — 응답의 shareToken 상태를 갱신.
export function useShareFeedback(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => enableShare(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: feedbackKeys.detail(sessionId) })
    },
    onError: () => toast.error('공유 링크 발급에 실패했어요. 다시 시도해 주세요.'),
  })
}

// 공유 해제 — 기존 링크는 즉시 무효화된다.
export function useUnshareFeedback(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => disableShare(sessionId),
    onSuccess: () => {
      toast.success('공유를 해제했어요. 기존 링크는 더 이상 열리지 않습니다.')
      void queryClient.invalidateQueries({ queryKey: feedbackKeys.detail(sessionId) })
    },
    onError: () => toast.error('공유 해제에 실패했어요. 다시 시도해 주세요.'),
  })
}

// 피드백 재생성 요청 — polling 이 다 소진되도록 피드백이 안 오는 실패 케이스의 복구 버튼.
// 409(이미 존재)는 그 사이 피드백이 도착한 것 → 에러가 아니라 재조회 신호다.
export function useRegenerateFeedback(sessionId: number) {
  const queryClient = useQueryClient()
  const invalidate = () =>
    void queryClient.resetQueries({ queryKey: feedbackKeys.detail(sessionId) })
  return useMutation({
    mutationFn: () => regenerateFeedback(sessionId),
    onSuccess: () => {
      toast.success('피드백을 다시 생성하고 있어요. 잠시만 기다려 주세요.')
      invalidate()
    },
    onError: (err) => {
      if (isApiError(err) && err.status === 409) {
        invalidate()
        return
      }
      toast.error('재생성 요청에 실패했어요. 잠시 후 다시 시도해 주세요.')
    },
  })
}

// 공개 페이지: 공유 토큰으로 피드백 조회(비인증, 재시도 없음).
export function useSharedFeedback(token: string) {
  return useQuery({
    queryKey: [...feedbackKeys.all, 'shared', token],
    queryFn: () => getSharedFeedback(token),
    retry: false,
  })
}
