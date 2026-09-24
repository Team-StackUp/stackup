import { apiClient } from '@/shared/api'
import type { AnalyzedDocument } from '../model/types'

export type DocumentFilter = {
  resumeId?: number
  repositoryId?: number
}

export async function fetchDocuments(
  filter: DocumentFilter = {},
): Promise<AnalyzedDocument[]> {
  const response = await apiClient.get<AnalyzedDocument[]>('/api/documents', {
    params: filter,
  })
  return response.data
}

// 상세 조회만 documentDownloadUrl(presigned) 을 포함한다.
export async function fetchDocument(id: number): Promise<AnalyzedDocument> {
  const response = await apiClient.get<AnalyzedDocument>(`/api/documents/${id}`)
  return response.data
}

// 분석에 실패한 문서를 같은 원본으로 다시 분석. 새 문서가 만들어지고 실패한 문서는 사라진다.
export async function reanalyzeDocument(id: number): Promise<{ documentId: number }> {
  const response = await apiClient.post<{ documentId: number }>(
    `/api/documents/${id}/reanalyze`,
  )
  return response.data
}
