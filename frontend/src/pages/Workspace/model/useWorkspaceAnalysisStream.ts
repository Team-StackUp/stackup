import { useCallback, useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createStreamToken } from '@/features/auth'
import { resumeKeys } from '@/features/resume'
import { repoKeys } from '@/features/repo'
import { documentKeys } from '@/features/analysis'
import { analysisProgress, useEventStream, workspaceStreamHealth } from '@/shared/hooks'
import type { StreamConnectionStatus } from '@/shared/hooks'

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
// 반환값: 연결 상태 — WorkspacePage 가 단절 배너를 그리는 데 사용.
export function useWorkspaceAnalysisStream(): StreamConnectionStatus {
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

  const status = useEventStream({
    path: '/realtime/stream/me',
    getToken,
    handlers: {
      DOC_STATE: onStateChange,
      REPO_STATE: onStateChange,
      ANALYSIS_PROGRESS: onProgress,
    },
  })

  // 목록 쿼리의 폴백 폴링 스위치. 'closed' 에서만 down — 최초 'connecting' 구간에
  // 폴링을 켜면 정상 부팅마다 불필요한 요청이 나간다. 언마운트 시 idle 로 되돌려
  // 워크스페이스 밖에서 폴링이 돌지 않게 한다.
  useEffect(() => {
    workspaceStreamHealth.set(status === 'closed' ? 'down' : 'up')
  }, [status])
  useEffect(() => () => workspaceStreamHealth.set('idle'), [])

  return status
}
