import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/ui'
import { endSession, interruptSession, startSession } from '../api/sessionApi'
import { sessionKeys } from './useSession'

export function useSessionLifecycle(sessionId: number) {
  const queryClient = useQueryClient()
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) })
    // 히스토리 목록·통계는 별도 키(['history', …])라 sessionKeys 로는 갱신되지 않는다 —
    // 종료 직후 히스토리로 이동하면 방금 세션이 안 보이던 문제.
    void queryClient.invalidateQueries({ queryKey: ['history'] })
  }

  // start/end 실패가 조용히 삼켜지면 사용자는 버튼이 왜 안 먹는지 알 수 없다.
  // 특히 '종료'는 ConfirmDialog 가 닫힌 뒤라, 실패를 알리지 않으면 종료된 줄 알고 탭을 닫는다.
  const start = useMutation({
    mutationFn: () => startSession(sessionId),
    onSuccess: invalidate,
    onError: () => toast.error('면접을 시작하지 못했어요. 다시 시도해 주세요.'),
  })
  const end = useMutation({
    mutationFn: () => endSession(sessionId),
    onSuccess: invalidate,
    onError: () => toast.error('면접을 종료하지 못했어요. 다시 시도해 주세요.'),
  })
  const interrupt = useMutation({
    mutationFn: () => interruptSession(sessionId),
    onSuccess: invalidate,
    onError: () => toast.error('면접을 중단하지 못했어요. 다시 시도해 주세요.'),
  })

  return { start, end, interrupt }
}
