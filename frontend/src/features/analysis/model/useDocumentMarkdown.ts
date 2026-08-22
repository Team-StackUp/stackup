import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api'

// 분석 원문(마크다운) 로드 — 계약상 유일한 마크다운 문서(docs/messaging.md §5.4)인데
// 지금까지 프론트 어디에도 렌더되지 않던 gap 을 메운다.
// presigned URL 직접 fetch 가 아니라 Core 프록시(`GET /api/documents/{id}/content`) 경유 —
// presigned 는 내부(MinIO) 호스트라 브라우저가 접근할 수 없다(TTS 오디오 프록시와 동일 이유).
export function useDocumentMarkdown(documentId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['analysis', 'document-markdown', documentId],
    enabled,
    staleTime: 5 * 60_000,
    queryFn: async ({ signal }) => {
      // 모달을 닫으면 react-query 가 abort — 원문 다운로드를 붙들고 있지 않는다.
      const res = await apiClient.get<string>(`/api/documents/${documentId}/content`, {
        signal,
        responseType: 'text',
      })
      return res.data
    },
  })
}
