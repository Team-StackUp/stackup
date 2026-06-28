import { apiClient } from '@/shared/api'
import type { CoverLetter, CoverLetterCreateRequest } from '../model/types'

export async function fetchCoverLetters(): Promise<CoverLetter[]> {
  const response = await apiClient.get<CoverLetter[]>('/api/cover-letters')
  return response.data
}

export async function createCoverLetter(
  body: CoverLetterCreateRequest,
): Promise<CoverLetter> {
  const response = await apiClient.post<CoverLetter>('/api/cover-letters', body)
  return response.data
}

export async function deleteCoverLetter(id: number): Promise<void> {
  await apiClient.delete(`/api/cover-letters/${id}`)
}
