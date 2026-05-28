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
