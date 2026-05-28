import { apiClient } from '@/shared/api'
import type {
  CandidateRepository,
  RegisteredRepository,
  RegisterRepositoryInput,
} from '../model/types'

export async function fetchRegisteredRepositories(): Promise<
  RegisteredRepository[]
> {
  const response = await apiClient.get<RegisteredRepository[]>(
    '/api/repositories',
  )
  return response.data
}

export async function fetchCandidateRepositories(
  page: number,
  perPage: number,
): Promise<CandidateRepository[]> {
  const response = await apiClient.get<CandidateRepository[]>(
    '/api/repositories/github',
    { params: { page, perPage } },
  )
  return response.data
}

export async function registerRepository(
  input: RegisterRepositoryInput,
): Promise<RegisteredRepository> {
  const response = await apiClient.post<RegisteredRepository>(
    '/api/repositories',
    input,
  )
  return response.data
}

export async function deleteRepository(id: number): Promise<void> {
  await apiClient.delete(`/api/repositories/${id}`)
}
