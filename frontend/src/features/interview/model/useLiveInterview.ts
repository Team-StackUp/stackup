import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { currentTurn } from '@/domain/session'
import type { Message } from '@/domain/session'
import { submitVoiceAnswer, fetchMessageSegmentObjectUrl } from '../api/messageApi'
import { createSegmentQueue } from '../lib/media/segmentAudioQueue'
import { sessionKeys, useSession } from './useSession'
import { messageKeys, useSessionMessages } from './useSessionMessages'
import { useSessionLifecycle } from './useSessionLifecycle'
import { useInterviewSocket } from './useInterviewSocket'
import { interviewEventAction } from './interviewEvent'
import { pendingAnswers, toOptimisticMessage } from './optimistic'
import type { OptimisticAnswer } from './optimistic'
import { applyDelta, isStreamingMessage, FOLLOWUP_GENERATING_TEXT } from './streamingBuffer'
import type { DeltaPayload } from './streamingBuffer'

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
  const [deltaBuffer, setDeltaBuffer] = useState<Record<number, string>>({})

  // ── 라이브 세그먼트 TTS ──────────────────────────────────────────────────
  const audioElRef = useRef<HTMLAudioElement | null>(null)
  const lastUrlRef = useRef<string | null>(null)
  const queueRef = useRef<ReturnType<typeof createSegmentQueue> | null>(null)
  const currentAudioMsgId = useRef<number | null>(null)
  const segmentedIds = useRef<Set<number>>(new Set())
  if (queueRef.current === null) {
    queueRef.current = createSegmentQueue(async (url) => {
      if (typeof Audio === 'undefined') return
      if (!audioElRef.current) audioElRef.current = new Audio()
      const el = audioElRef.current
      if (lastUrlRef.current) URL.revokeObjectURL(lastUrlRef.current)
      lastUrlRef.current = url
      el.src = url
      try { await el.play() } catch { /* autoplay 차단 등은 무시 */ }
    })
  }

  // 서버가 sequenceNumber asc 로 주지만, 순서를 코드에서 명시적으로 보장한다.
  const serverMessages = [...(messagesQuery.data ?? [])].sort(
    (a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0),
  )
  const pending = pendingAnswers(optimistic, serverMessages)

  // 스트리밍 중인 메시지는 deltaBuffer의 누적 텍스트로 content를 오버라이드한다.
  const mergedMessages = serverMessages.map((m) => {
    const buffered = deltaBuffer[m.id ?? -1]
    if (buffered !== undefined && isStreamingMessage(m, buffered)) {
      return { ...m, content: buffered }
    }
    return m
  })

  const items: ThreadItem[] = [
    ...mergedMessages.map((m) => ({ ...m, key: `m-${m.id}` })),
    ...pending.map((o) => ({ ...toOptimisticMessage(o), key: `opt-${o.tempId}` })),
  ]

  // 세그먼트 오디오 엘리먼트 ended 리스너 — 마운트 1회.
  useEffect(() => {
    if (typeof Audio === 'undefined') return
    if (!audioElRef.current) audioElRef.current = new Audio()
    const el = audioElRef.current
    const onEnded = () => queueRef.current?.onEnded()
    el.addEventListener('ended', onEnded)
    return () => {
      el.removeEventListener('ended', onEnded)
      if (lastUrlRef.current) URL.revokeObjectURL(lastUrlRef.current)
    }
  }, [])

  // 서버에 반영된 낙관적 답변은 상태에서 제거(세션 내내 누적 방지).
  useEffect(() => {
    setOptimistic((prev) => {
      const next = pendingAnswers(prev, messagesQuery.data ?? [])
      return next.length === prev.length ? prev : next
    })
  }, [messagesQuery.data])

  // 서버에서 최종 content가 도착한 메시지의 버퍼를 정리한다.
  useEffect(() => {
    setDeltaBuffer((prev) => {
      const next = { ...prev }
      let changed = false
      for (const m of serverMessages) {
        if (m.id !== undefined && next[m.id] !== undefined && m.content !== FOLLOWUP_GENERATING_TEXT) {
          delete next[m.id]
          changed = true
        }
      }
      return changed ? next : prev
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
      } else if (action.kind === 'append-delta') {
        const payload = (frame.data as { data?: DeltaPayload } | undefined)?.data
        if (payload && typeof payload.messageId === 'number') {
          setDeltaBuffer((b) => applyDelta(b, payload))
        }
      } else if (action.kind === 'queue-audio') {
        const p = (frame.data as { data?: { messageId: number; seq: number; ext: string } } | undefined)?.data
        if (p && typeof p.messageId === 'number') {
          segmentedIds.current.add(p.messageId)
          if (currentAudioMsgId.current !== p.messageId) {
            currentAudioMsgId.current = p.messageId
            queueRef.current?.reset()
          }
          void fetchMessageSegmentObjectUrl(sessionId, p.messageId, p.seq, p.ext)
            .then((url) => queueRef.current?.enqueue({ seq: p.seq, url }))
            .catch(() => { /* best-effort */ })
        }
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

  // 음성 답변은 REST 업로드(multipart). 성공 시 placeholder 메시지를
  // 받으므로 목록을 무효화해 "음성 인식 중…" 버블을 띄운다.
  // 이후 STT 완료는 callback.voice → SESSION_MESSAGE SSE 로 갱신된다.
  const voiceMutation = useMutation({
    mutationFn: (audio: Blob) => submitVoiceAnswer(sessionId, audio, crypto.randomUUID()),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: messageKeys.list(sessionId) }),
  })

  const submitVoice = useCallback(
    (audio: Blob) => voiceMutation.mutate(audio),
    [voiceMutation],
  )

  // 서버 메시지 기준으로 가장 최근 면접관 메시지가 여전히 sentinel이면 스트리밍 진행 중.
  const latestServerQuestion = [...serverMessages].reverse().find((m) => m.role === 'INTERVIEWER')
  const questionStreaming = latestServerQuestion?.content === FOLLOWUP_GENERATING_TEXT

  const wasSegmented = useCallback((id: number) => segmentedIds.current.has(id), [])

  return {
    session: sessionQuery.data,
    status,
    items,
    turn: currentTurn(items),
    connection,
    submitAnswer,
    submitVoice,
    voiceUploading: voiceMutation.isPending,
    voiceError: voiceMutation.isError,
    endSession: () => end.mutate(),
    isLoading: sessionQuery.isLoading,
    questionStreaming,
    wasSegmented,
  }
}
