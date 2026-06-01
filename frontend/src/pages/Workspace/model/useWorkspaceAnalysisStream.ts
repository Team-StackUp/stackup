import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createStreamToken } from '@/features/auth'
import { resumeKeys } from '@/features/resume'
import { repoKeys } from '@/features/repo'
import { documentKeys } from '@/features/analysis'
import { useEventStream } from '@/shared/hooks'

// 이력서·레포·문서 쿼리를 무효화 → 화면이 자동으로 최신 상태로 갱신된다.
export function useWorkspaceAnalysisStream() {
  const queryClient = useQueryClient()

  const getToken = useCallback(() => createStreamToken(), [])

  const invalidateAll = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: resumeKeys.all })
    void queryClient.invalidateQueries({ queryKey: repoKeys.registered })
    void queryClient.invalidateQueries({ queryKey: documentKeys.all })
  }, [queryClient])

  useEventStream({
    path: '/realtime/stream/me',
    getToken,
    handlers: {
      DOC_STATE: invalidateAll,
      REPO_STATE: invalidateAll,
    },
  })
}
