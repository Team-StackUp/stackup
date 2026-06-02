import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createStreamToken } from '@/features/auth'
import { resumeKeys } from '@/features/resume'
import { repoKeys } from '@/features/repo'
import { documentKeys } from '@/features/analysis'
import { analysisProgress, useEventStream } from '@/shared/hooks'

// RealTime SSE data 봉투: { data: <payload>, traceId }.
type StreamData<T> = { data?: T; traceId?: string | null }

type StatePayload = { targetType?: string; targetId?: number }
type ProgressPayload = {
  targetType?: string
  targetId?: number
  phase?: string
  message?: string
}

function unwrap<T>(raw: unknown): T | undefined {
  return (raw as StreamData<T> | null)?.data
}

// 이력서·레포·문서 쿼리를 무효화 → 화면이 자동으로 최신 상태로 갱신된다.
// 추가로 ANALYSIS_PROGRESS(단계별 진행)는 쿼리 무효화 없이 진행 store 만 갱신한다.
export function useWorkspaceAnalysisStream() {
  const queryClient = useQueryClient()

  const getToken = useCallback(() => createStreamToken(), [])

  // 종료 상태(REPO_STATE/DOC_STATE): 진행 표시 제거 + 목록 갱신.
  const onStateChange = useCallback(
    (raw: unknown) => {
      const data = unwrap<StatePayload>(raw)
      if (data?.targetType && typeof data.targetId === 'number') {
        analysisProgress.clear(data.targetType, data.targetId)
      }
      void queryClient.invalidateQueries({ queryKey: resumeKeys.all })
      void queryClient.invalidateQueries({ queryKey: repoKeys.registered })
      void queryClient.invalidateQueries({ queryKey: documentKeys.all })
    },
    [queryClient],
  )

  // 진행 단계(휘발성): 쿼리 무효화 없이 store 만 갱신.
  const onProgress = useCallback((raw: unknown) => {
    const data = unwrap<ProgressPayload>(raw)
    if (!data?.targetType || typeof data.targetId !== 'number') return
    analysisProgress.set(data.targetType, data.targetId, {
      phase: data.phase ?? '',
      message: data.message ?? '',
    })
  }, [])

  useEventStream({
    path: '/realtime/stream/me',
    getToken,
    handlers: {
      DOC_STATE: onStateChange,
      REPO_STATE: onStateChange,
      ANALYSIS_PROGRESS: onProgress,
    },
  })
}
