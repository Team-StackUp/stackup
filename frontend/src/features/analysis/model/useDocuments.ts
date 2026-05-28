import { useQuery } from '@tanstack/react-query'
import { fetchDocuments, type DocumentFilter } from '../api/analysis'
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
