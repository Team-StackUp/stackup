import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/ui'
import { resumeSession } from '../api/sessionApi'
import { sessionKeys } from './useSession'
import { messageKeys } from './useSessionMessages'

/**
 * 중단된 면접 이어하기. 서버가 끊긴 턴까지 복구하므로(다음 질문 발행 등)
 * 세션과 메시지를 모두 다시 읽어야 화면이 살아난다.
 */
export function useResumeSession(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => resumeSession(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) })
      void queryClient.invalidateQueries({ queryKey: messageKeys.list(sessionId) })
      void queryClient.invalidateQueries({ queryKey: sessionKeys.all })
      toast.success('면접을 이어서 진행합니다')
    },
    onError: () =>
      toast.error('면접을 이어가지 못했어요. 잠시 후 다시 시도해 주세요.'),
  })
}
