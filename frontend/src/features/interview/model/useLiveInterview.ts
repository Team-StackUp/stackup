import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { currentTurn } from '@/domain/session'
import type { Message } from '@/domain/session'
import { toast } from '@/shared/ui'
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
import type { DeliveryMode } from './useDeliveryMode'

export type ThreadItem = Message & { key: string; streaming?: boolean }
export type ConnectionStatus = 'connecting' | 'open' | 'closed'

export function useLiveInterview(sessionId: number, deliveryMode: DeliveryMode = 'text') {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const sessionQuery = useSession(sessionId)
  const status = sessionQuery.data?.status
  const messagesQuery = useSessionMessages(sessionId, status === 'IN_PROGRESS' || status === 'COMPLETED')
  const { end } = useSessionLifecycle(sessionId)

  const [optimistic, setOptimistic] = useState<OptimisticAnswer[]>([])
  // 전송 실패로 롤백된 답변 본문 — 컴포저가 입력창을 복원하는 데 사용(nonce 로 매 실패마다 트리거).
  const [restoreDraft, setRestoreDraft] = useState<{ content: string; nonce: number } | null>(null)
  const [connection, setConnection] = useState<ConnectionStatus>('connecting')
  const [deltaBuffer, setDeltaBuffer] = useState<Record<number, string>>({})
  // 라이브 세그먼트 오디오가 지금 재생 중인 메시지(아바타·질문 카드의 '말하는 중' 표시용).
  const [speakingAudio, setSpeakingAudio] = useState<{ msgId: number | null; playing: boolean }>({
    msgId: null,
    playing: false,
  })

  // ── 라이브 세그먼트 TTS ──────────────────────────────────────────────────
  const audioElRef = useRef<HTMLAudioElement | null>(null)
  const lastUrlRef = useRef<string | null>(null)
  const queueRef = useRef<ReturnType<typeof createSegmentQueue> | null>(null)
  const currentAudioMsgId = useRef<number | null>(null)
  const segmentedIds = useRef<Set<number>>(new Set())
  // 소켓 onEvent 클로저에서 최신 모드를 읽기 위한 ref. 텍스트 모드는 음성 자동재생 안 함.
  const deliveryModeRef = useRef(deliveryMode)
  deliveryModeRef.current = deliveryMode

  // 텍스트 답변은 WS fire-and-forget 라 ack 가 없다. 제출 후 일정 시간 내 서버 반영이
  // 안 되면 stuck 된 낙관적 답변을 롤백한다. tempId 별 타이머 + 최신 optimistic 미러 ref.
  const optimisticRef = useRef<OptimisticAnswer[]>([])
  optimisticRef.current = optimistic
  const answerTimers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())
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
      return { ...m, content: buffered, streaming: true as const }
    }
    return m
  })

  const items: ThreadItem[] = [
    ...mergedMessages.map((m) => ({ ...m, key: `m-${m.id}` })),
    ...pending.map((o) => ({ ...toOptimisticMessage(o), key: `opt-${o.tempId}` })),
  ]

  // 세그먼트 오디오 엘리먼트 재생 리스너 — 마운트 1회.
  // play/pause/ended 로 '말하는 중' 상태를 갱신해 아바타·질문 카드가 동기화되게 한다.
  useEffect(() => {
    if (typeof Audio === 'undefined') return
    if (!audioElRef.current) audioElRef.current = new Audio()
    const el = audioElRef.current
    const onEnded = () => {
      setSpeakingAudio((s) => ({ ...s, playing: false }))
      queueRef.current?.onEnded()
    }
    const onPlay = () => setSpeakingAudio({ msgId: currentAudioMsgId.current, playing: true })
    const onPause = () => setSpeakingAudio((s) => ({ ...s, playing: false }))
    el.addEventListener('ended', onEnded)
    el.addEventListener('play', onPlay)
    el.addEventListener('pause', onPause)
    return () => {
      el.removeEventListener('ended', onEnded)
      el.removeEventListener('play', onPlay)
      el.removeEventListener('pause', onPause)
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
        // 헤더 질문 카운트(질문 N/M)는 session.totalQuestionCount 기반이라,
        // 새 메시지(질문)마다 세션도 갱신해 진행도가 라이브로 반영되게 한다.
        void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) })
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
        // 텍스트 모드에서는 음성을 자동재생하지 않는다(읽는 도중 끼어들기 방지).
        // 수동 재생은 StageQuestion 의 전체 파일(callback.tts) 경로로 제공된다.
        if (deliveryModeRef.current !== 'voice') return
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

  const ANSWER_ACK_TIMEOUT_MS = 10_000

  const submitAnswer = useCallback(
    (content: string) => {
      const tempId = crypto.randomUUID()
      setOptimistic((prev) => [...prev, { tempId, content }])
      socketSubmit(content, tempId)
      // ack 타임아웃: 시간 내 서버 반영(낙관적 소진)이 없으면 롤백 + 입력 복원 + 안내.
      const timer = setTimeout(() => {
        answerTimers.current.delete(tempId)
        if (!optimisticRef.current.some((o) => o.tempId === tempId)) return // 이미 반영됨
        setOptimistic((prev) => prev.filter((o) => o.tempId !== tempId))
        setRestoreDraft({ content, nonce: Date.now() })
        toast.error('답변 전송에 실패했어요. 입력을 복원했으니 다시 시도해 주세요.')
      }, ANSWER_ACK_TIMEOUT_MS)
      answerTimers.current.set(tempId, timer)
    },
    [socketSubmit],
  )

  // 언마운트 시 남은 ack 타이머 정리(언마운트 후 setState/toast 방지).
  useEffect(() => {
    const timers = answerTimers.current
    return () => {
      timers.forEach((t) => clearTimeout(t))
      timers.clear()
    }
  }, [])

  // 음성 답변은 REST 업로드(multipart). 성공 시 placeholder 메시지를
  // 받으므로 목록을 무효화해 "음성 인식 중…" 버블을 띄운다.
  // 이후 STT 완료는 callback.voice → SESSION_MESSAGE SSE 로 갱신된다.
  const voiceMutation = useMutation({
    mutationFn: (audio: Blob) => submitVoiceAnswer(sessionId, audio, crypto.randomUUID()),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: messageKeys.list(sessionId) }),
    onError: () =>
      toast.error('음성 답변 업로드에 실패했어요. 다시 시도해 주세요.'),
  })

  const submitVoice = useCallback(
    (audio: Blob) => voiceMutation.mutate(audio),
    [voiceMutation],
  )

  // 서버 메시지 기준으로 가장 최근 면접관 메시지가 여전히 sentinel이면 스트리밍 진행 중.
  const latestServerQuestion = [...serverMessages].reverse().find((m) => m.role === 'INTERVIEWER')
  const questionStreaming = latestServerQuestion?.content === FOLLOWUP_GENERATING_TEXT

  const wasSegmented = useCallback((id: number) => segmentedIds.current.has(id), [])

  // 해당 메시지의 라이브 세그먼트 오디오가 지금 재생 중인지.
  const isSpeaking = useCallback(
    (id: number) => speakingAudio.msgId === id && speakingAudio.playing,
    [speakingAudio],
  )

  // 첫 질문이 실제 content 를 갖고 도착했는지. 면접 스테이지 진입 전에 이걸 기다려
  // 사용자가 스테이지에 들어서면 바로 질문을 볼 수 있게 한다(빈 대기 화면 회피).
  const firstQuestionReady = items.some((m) => {
    if (m.role !== 'INTERVIEWER') return false
    const c = (m.content ?? '').trim()
    return c.length > 0 && c !== FOLLOWUP_GENERATING_TEXT
  })

  return {
    session: sessionQuery.data,
    status,
    items,
    turn: currentTurn(items),
    connection,
    submitAnswer,
    restoreDraft,
    submitVoice,
    voiceUploading: voiceMutation.isPending,
    voiceError: voiceMutation.isError,
    endSession: () => end.mutate(),
    isLoading: sessionQuery.isLoading,
    questionStreaming,
    wasSegmented,
    isSpeaking,
    firstQuestionReady,
  }
}
