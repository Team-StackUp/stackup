import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAnalysisFallbackPolling } from '@/shared/hooks'
import { isApiError } from '@/shared/api'
import { toast } from '@/shared/ui'
import {
  deleteRepository,
  fetchCandidateRepositories,
  fetchRegisteredRepositories,
  registerRepository,
} from '../api/repo'
import type { CandidateRepository, RegisteredRepository } from './types'

const errMessage = (e: unknown, fallback: string) =>
  isApiError(e) ? e.message : fallback

export const repoKeys = {
  // registered 와 candidates 모두 ['repositories'] prefix → 한 번에 invalidate 가능
  registered: ['repositories'] as const,
  candidates: (page: number, perPage: number) =>
    ['repositories', 'github', page, perPage] as const,
}

export function useRegisteredRepositories() {
  // 분석 SSE 가 죽은 동안만 5s 폴링 (useResumes 와 동일한 이유).
  const fallbackPolling = useAnalysisFallbackPolling()
  return useQuery<RegisteredRepository[]>({
    queryKey: repoKeys.registered,
    queryFn: fetchRegisteredRepositories,
    refetchInterval: fallbackPolling,
  })
}

export function useCandidateRepositories(
  page: number,
  perPage: number,
  enabled: boolean,
) {
  return useQuery<CandidateRepository[]>({
    queryKey: repoKeys.candidates(page, perPage),
    queryFn: () => fetchCandidateRepositories(page, perPage),
    enabled,
  })
}

export function useRegisterRepository() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: registerRepository,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: repoKeys.registered })
      toast.success('레포지토리를 등록했어요. 분석이 곧 시작됩니다.')
    },
  })
}

export function useDeleteRepository() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteRepository,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: repoKeys.registered })
      // 분석 결과(documents)는 analysis feature 소유라 직접 import 하지 않고(FSD 동일레이어 금지)
      // 키 리터럴 ['documents'] 로 무효화 — 삭제는 클라이언트 액션이라 SSE 가 오지 않는다.
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
      toast.success('레포지토리를 삭제했어요')
    },
    onError: (e) => toast.error(errMessage(e, '레포지토리 삭제에 실패했어요')),
  })
}
