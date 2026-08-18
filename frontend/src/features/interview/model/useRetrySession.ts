import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from '@/shared/ui'
import { retrySession } from '../api/sessionApi'
import { sessionKeys } from './useSession'

/**
 * 같은 설정으로 다시 면접. 서버가 설정을 복사하고, 그 사이 삭제·재분석 중인 자료는 빼고 잇는다.
 *
 * @param sourceContextCount 원본 세션이 연결하고 있던 자료 수. 새 세션에 붙은 수와 다르면
 *   조용히 빠진 것이므로 사용자에게 알린다 — 모르고 시작하면 질문 근거가 달라진 걸 알 수 없다.
 */
export function useRetrySession(sourceContextCount?: number) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: retrySession,
    onSuccess: (session) => {
      void queryClient.invalidateQueries({ queryKey: sessionKeys.all })
      const linked = session.contextDocumentIds?.length ?? 0
      if (sourceContextCount != null && linked < sourceContextCount) {
        toast.info(
          `삭제된 자료 ${sourceContextCount - linked}개는 제외하고 시작합니다.`,
        )
      }
      navigate(`/sessions/${session.id}`)
    },
    onError: () => {
      toast.error('면접을 다시 만들지 못했어요. 잠시 후 다시 시도해 주세요.')
    },
  })
}
