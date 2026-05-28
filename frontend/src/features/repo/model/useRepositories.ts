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
    },
  })
}
