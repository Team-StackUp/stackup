import { useMutation, useQueryClient } from '@tanstack/react-query'
import { endSession, interruptSession, startSession } from '../api/sessionApi'
import { sessionKeys } from './useSession'

export function useSessionLifecycle(sessionId: number) {
  const queryClient = useQueryClient()
  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) })

  const start = useMutation({ mutationFn: () => startSession(sessionId), onSuccess: invalidate })
  const end = useMutation({ mutationFn: () => endSession(sessionId), onSuccess: invalidate })
  const interrupt = useMutation({ mutationFn: () => interruptSession(sessionId), onSuccess: invalidate })

  return { start, end, interrupt }
}
