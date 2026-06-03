import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteRepository,
  fetchCandidateRepositories,
  fetchRegisteredRepositories,
  registerRepository,
} from '../api/repo'
import type { CandidateRepository, RegisteredRepository } from './types'

export const repoKeys = {
  // registered 와 candidates 모두 ['repositories'] prefix → 한 번에 invalidate 가능
  registered: ['repositories'] as const,
  candidates: (page: number, perPage: number) =>
    ['repositories', 'github', page, perPage] as const,
}

export function useRegisteredRepositories() {
  return useQuery<RegisteredRepository[]>({
    queryKey: repoKeys.registered,
    queryFn: fetchRegisteredRepositories,
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
    },
  })
}
