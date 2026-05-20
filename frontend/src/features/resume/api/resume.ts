import { apiClient } from '@/shared/api'
import type {
  PageResponse,
  ResumeResponse,
} from '@/features/resume/model/types'

export async function uploadResume(file: File): Promise<ResumeResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<ResumeResponse>('/api/resumes', form)
  return data
}

export async function listResumes(
  page = 0,
  size = 20,
): Promise<PageResponse<ResumeResponse>> {
  const { data } = await apiClient.get<PageResponse<ResumeResponse>>(
    '/api/resumes', { params: { page, size, sort: 'createdAt,desc' } },
  )
  return data
}

export async function getResume(id: number): Promise<ResumeResponse> {
  const { data } = await apiClient.get<ResumeResponse>(`/api/resumes/${id}`)
  return data
}

export async function deleteResume(id: number): Promise<void> {
  await apiClient.delete(`/api/resumes/${id}`)
}