// 분석 문서(RAG 소스)의 출처 유형 — 이력서/레포/웹/자소서. features 간 공유(라벨 일관성).
export type DocumentSourceType = 'RESUME' | 'REPOSITORY' | 'WEB' | 'COVER_LETTER'

export const DOCUMENT_SOURCE_LABEL: Record<DocumentSourceType, string> = {
  RESUME: '이력서',
  REPOSITORY: '레포지토리',
  WEB: '웹',
  COVER_LETTER: '자소서',
}

// 임의 문자열(서버 ENUM)도 안전하게 한국어 라벨로. 미매핑이면 원문 반환.
export function documentSourceLabel(source: string): string {
  return DOCUMENT_SOURCE_LABEL[source as DocumentSourceType] ?? source
}
