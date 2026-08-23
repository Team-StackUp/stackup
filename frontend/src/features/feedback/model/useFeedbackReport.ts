import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api'
import { feedbackKeys } from './useFeedback'

// AI 학습 리포트(마크다운) 로드 — AI 가 피드백 생성 시 저장한 reportS3Key 파일을
// Core 프록시(`GET /api/sessions/{id}/feedback/report`)로 받는다. presigned 는 내부(MinIO)
// 호스트라 브라우저가 접근할 수 없다(분석 원문 /content 프록시와 동일 이유).
// 소유자 전용 — 공개 공유 응답에는 reportFilePath 자체가 실리지 않아 진입점이 없다.
export function useFeedbackReport(sessionId: number, enabled: boolean) {
  return useQuery({
    // detail 키의 하위로 둔다 — 재생성(useRegenerateFeedback)의 resetQueries(prefix 매칭)가
    // 리포트 캐시도 함께 비워, 재생성 후 이전 리포트가 staleTime 동안 보이는 일을 막는다.
    queryKey: [...feedbackKeys.detail(sessionId), 'report'],
    enabled,
    staleTime: 5 * 60_000,
    queryFn: async ({ signal }) => {
      const res = await apiClient.get<string>(`/api/sessions/${sessionId}/feedback/report`, {
        signal,
        responseType: 'text',
      })
      return res.data
    },
  })
}
