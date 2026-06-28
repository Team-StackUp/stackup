export type AnalysisStatus = 'PROCESSING' | 'ANALYZED' | 'FAILED'

export type AnalysisSourceType = 'RESUME' | 'REPOSITORY' | 'WEB' | 'COVER_LETTER'

export type AnalyzedDocument = {
  id: number
  sourceType: AnalysisSourceType
  sourceId: number
  documentPath: string | null
  // 목록 응답에서는 비어 있고, 상세 조회 시에만 10분 TTL presigned URL 이 채워진다.
  documentDownloadUrl: string | null
  summary: string | null
  techStack: string[]
  embeddingChunkCount: number
  analysisStatus: AnalysisStatus
  errorCode: string | null
  errorMessage: string | null
  status: string
  createdAt: string
  updatedAt: string
}

// /realtime/stream/me 의 DOC_STATE / REPO_STATE 이벤트 data 의 payload 필드.
export type AnalysisEventPayload = {
  targetType: AnalysisSourceType
  targetId: number
  status: string
  summary?: string | null
  techStack?: string[]
  documentPath?: string | null
  embeddingChunkCount?: number | null
  errorCode?: string | null
  errorMessage?: string | null
}

// 백엔드 SseEvent 봉투. event data 전체 형태.
export type SseEnvelope<T> = {
  id: string | null
  type: string
  payload: T
  timestamp: string
  traceId: string | null
}
