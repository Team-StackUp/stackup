import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteRepo,
  getRepo,
  listCandidates,
  listRegistered,
  registerRepo,
} from '@/features/repo/api/repo'
import type {
  PageResponse,
  RegisteredRepositoryResponse,
} from '@/features/repo/model/types'

const REGISTERED_KEY = 'repositories' as const
const CANDIDATES_KEY = 'repositories-candidates' as const
const POLL_INTERVAL_MS = 2_000

function hasInProgress(
  page: PageResponse<RegisteredRepositoryResponse> | undefined,
): boolean {
  if (!page) return false
  return page.content.some(
    (r) => r.status === 'PENDING' || r.status === 'ANALYZING',
  )
}

export function useRegisteredReposQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: [REGISTERED_KEY, page, size],
    queryFn: () => listRegistered(page, size),
    refetchInterval: (query) =>
      hasInProgress(query.state.data) ? POLL_INTERVAL_MS : false,
  })
}

export function useRegisteredRepoQuery(id: number | null) {
  return useQuery({
    queryKey: [REGISTERED_KEY, 'detail', id],
    queryFn: () => getRepo(id as number),
    enabled: id !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PENDING' || status === 'ANALYZING'
        ? POLL_INTERVAL_MS
        : false
    },
  })
}

export function useCandidateReposQuery(page = 1, perPage = 30) {
  return useQuery({
    queryKey: [CANDIDATES_KEY, page, perPage],
    queryFn: () => listCandidates(page, perPage),
  })
}

export function useRegisterRepoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (githubRepoId: number) => registerRepo(githubRepoId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [REGISTERED_KEY] })
      qc.invalidateQueries({ queryKey: [CANDIDATES_KEY] })
    },
  })
}

export function useDeleteRepoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteRepo(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [REGISTERED_KEY] })
      qc.invalidateQueries({ queryKey: [CANDIDATES_KEY] })
    },
  })
}
