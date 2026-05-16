import { apiClient } from '@/shared/api' 
import type {
  CandidateRepositoryPageResponse,
  PageResponse,
  RegisteredRepositoryResponse,
} from '@/features/repo/model/types'

export async function listCandidates(
  page = 1,
  perPage = 30,
): Promise<CandidateRepositoryPageResponse> {
  const { data } = await apiClient.get<CandidateRepositoryPageResponse>(
    '/api/repositories/github', { params: { page, perPage } },
  )
  return data
}

export async function registerRepo(
  githubRepoId: number,
): Promise<RegisteredRepositoryResponse> {
  const { data } = await apiClient.post<RegisteredRepositoryResponse>(
    '/api/repositories', { githubRepoId },
  )
  return data
}

export async function listRegistered(
  page = 0,
  size = 20,
): Promise<PageResponse<RegisteredRepositoryResponse>> {
  const { data } = await apiClient.get<PageResponse<RegisteredRepositoryResponse>>(
    '/api/repositories', { params: { page, size, sort: 'createdAt,desc' } },
  )
  return data
}

export async function getRepo(
  id: number,
): Promise<RegisteredRepositoryResponse> {
  const { data } = await apiClient.get<RegisteredRepositoryResponse>(
    `/api/repositories/${id}`,
  )
  return data
}

export async function deleteRepo(id: number): Promise<void> {
  await apiClient.delete(`/api/repositories/${id}`)
}