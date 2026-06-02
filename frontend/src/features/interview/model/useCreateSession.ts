import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createSession } from '../api/sessionApi'
import { sessionKeys } from './useSession'

export function useCreateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createSession,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: sessionKeys.all })
    },
  })
}
