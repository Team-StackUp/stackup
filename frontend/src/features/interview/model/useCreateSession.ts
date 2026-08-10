import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/ui'
import { createSession } from '../api/sessionApi'
import { sessionKeys } from './useSession'

export function useCreateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createSession,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: sessionKeys.all })
    },
    // 제품의 핵심 전환(면접 생성)이 실패했는데 아무 표시가 없으면 사용자는
    // 버튼만 반복해서 누른다 — 유일하게 onError 가 빠져 있던 뮤테이션이었다.
    onError: () => {
      toast.error('면접을 만들지 못했어요. 잠시 후 다시 시도해 주세요.')
    },
  })
}
