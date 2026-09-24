import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/ui'
import { fetchDocuments, reanalyzeDocument, type DocumentFilter } from '../api/analysis'
import type { AnalyzedDocument } from './types'

export const documentKeys = {
  all: ['documents'] as const,
  list: (filter: DocumentFilter) => ['documents', filter] as const,
}

export function useDocuments(filter: DocumentFilter = {}) {
  return useQuery<AnalyzedDocument[]>({
    queryKey: documentKeys.list(filter),
    queryFn: () => fetchDocuments(filter),
  })
}

export function useReanalyzeDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: number) => reanalyzeDocument(documentId),
    // 실패 문서가 사라지고 PROCESSING 문서가 생기므로 목록 전체를 다시 받는다.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: documentKeys.all }),
    onError: () => toast.error('다시 분석하지 못했어요. 잠시 후 시도해 주세요.'),
  })
}
