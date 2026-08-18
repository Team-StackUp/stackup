import { apiClient } from '@/shared/api'
import type { Resume } from '../model/types'

export async function fetchResumes(): Promise<Resume[]> {
  const response = await apiClient.get<Resume[]>('/api/resumes')
  return response.data
}

// multipart 업로드. apiClient 의 기본 Content-Type 이 application/json 이라
// 그대로 두면 axios 가 FormData 를 JSON 으로 직렬화해 버린다(415 발생).
// 요청별로 multipart/form-data 로 덮어쓰면 axios 가 boundary 를 다시 채워 전송한다.
export async function uploadResume(file: File): Promise<Resume> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<Resume>('/api/resumes', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}

// 웹 이력서(URL) 등록. 파일 업로드 없이 URL 만 보내고 본문 추출은 서버(AI)가 한다.
export async function registerWebResume(url: string): Promise<Resume> {
  const response = await apiClient.post<Resume>('/api/resumes/web', { url })
  return response.data
}

export async function deleteResume(id: number): Promise<void> {
  await apiClient.delete(`/api/resumes/${id}`)
}
