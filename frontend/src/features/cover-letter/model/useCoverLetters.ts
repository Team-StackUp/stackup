import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAnalysisFallbackPolling } from '@/shared/hooks'
import { isApiError } from '@/shared/api'
import { toast } from '@/shared/ui'
import {
  createCoverLetter,
  deleteCoverLetter,
  fetchCoverLetters,
} from '../api/coverLetter'
import type { CoverLetter } from './types'

const errMessage = (e: unknown, fallback: string) =>
  isApiError(e) ? e.message : fallback

export const coverLetterKeys = {
  all: ['cover-letters'] as const,
}

export function useCoverLetters() {
  // 분석 SSE 가 죽은 동안만 5s 폴링 (useResumes 와 동일한 이유).
  const fallbackPolling = useAnalysisFallbackPolling()
  return useQuery<CoverLetter[]>({
    queryKey: coverLetterKeys.all,
    queryFn: fetchCoverLetters,
    refetchInterval: fallbackPolling,
  })
}

export function useCreateCoverLetter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createCoverLetter,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: coverLetterKeys.all })
      toast.success('자소서를 저장했어요. 분석이 곧 시작됩니다.')
    },
    onError: (e) => toast.error(errMessage(e, '자소서 저장에 실패했어요')),
  })
}

export function useDeleteCoverLetter() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteCoverLetter,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: coverLetterKeys.all })
      // 분석 결과(documents)는 analysis feature 소유라 직접 import 하지 않고(FSD 동일레이어 금지)
      // 키 리터럴 ['documents'] 로 무효화 — 삭제는 클라이언트 액션이라 SSE 가 오지 않는다.
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
      toast.success('자소서를 삭제했어요')
    },
    onError: (e) => toast.error(errMessage(e, '자소서 삭제에 실패했어요')),
  })
}
