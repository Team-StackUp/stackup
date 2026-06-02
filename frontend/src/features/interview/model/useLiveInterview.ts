import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { currentTurn } from '@/domain/session'
import type { Message } from '@/domain/session'
import { sessionKeys, useSession } from './useSession'
import { messageKeys, useSessionMessages } from './useSessionMessages'
import { useSessionLifecycle } from './useSessionLifecycle'
import { useInterviewSocket } from './useInterviewSocket'
import { interviewEventAction } from './interviewEvent'
import { pendingAnswers, toOptimisticMessage } from './optimistic'
import type { OptimisticAnswer } from './optimistic'

export type ThreadItem = Message & { key: string }
export type ConnectionStatus = 'connecting' | 'open' | 'closed'

export function useLiveInterview(sessionId: number) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const sessionQuery = useSession(sessionId)
  const status = sessionQuery.data?.status
  const messagesQuery = useSessionMessages(sessionId, status === 'IN_PROGRESS' || status === 'COMPLETED')
  const { end } = useSessionLifecycle(sessionId)

  const [optimistic, setOptimistic] = useState<OptimisticAnswer[]>([])
  const [connection, setConnection] = useState<ConnectionStatus>('connecting')

  // 서버가 sequenceNumber asc 로 주지만, 순서를 코드에서 명시적으로 보장한다.
  const serverMessages = [...(messagesQuery.data ?? [])].sort(
    (a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0),
  )
  const pending = pendingAnswers(optimistic, serverMessages)
  const items: ThreadItem[] = [
    ...serverMessages.map((m) => ({ ...m, key: `m-${m.id}` })),
    ...pending.map((o) => ({ ...toOptimisticMessage(o), key: `opt-${o.tempId}` })),
  ]

  // 서버에 반영된 낙관적 답변은 상태에서 제거(세션 내내 누적 방지).
  useEffect(() => {
    setOptimistic((prev) => {
      const next = pendingAnswers(prev, messagesQuery.data ?? [])
      return next.length === prev.length ? prev : next
    })
  }, [messagesQuery.data])

  const { submitAnswer: socketSubmit } = useInterviewSocket({
    sessionId,
    enabled: status === 'IN_PROGRESS',
    onStatusChange: setConnection,
    onEvent: (frame) => {
      const action = interviewEventAction(frame.event)
      if (action.kind === 'refetch-messages') {
        void queryClient.invalidateQueries({ queryKey: messageKeys.list(sessionId) })
      } else if (action.kind === 'refetch-session') {
        void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) })
      } else if (action.kind === 'redirect-feedback') {
        navigate(`/sessions/${sessionId}/feedback`)
      }
    },
  })

  const submitAnswer = useCallback(
    (content: string) => {
      const tempId = crypto.randomUUID()
      setOptimistic((prev) => [...prev, { tempId, content }])
      socketSubmit(content, tempId)
    },
    [socketSubmit],
  )

  return {
    session: sessionQuery.data,
    status,
    items,
    turn: currentTurn(items),
    connection,
    submitAnswer,
    endSession: () => end.mutate(),
    isLoading: sessionQuery.isLoading,
  }
}
