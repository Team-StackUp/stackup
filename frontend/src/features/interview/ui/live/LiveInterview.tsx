import { Spinner } from '@/shared/ui/Spinner'
import { useLiveInterview } from '../../model/useLiveInterview'
import { useDeliveryMode } from '../../model/useDeliveryMode'
import { InterviewStage } from './InterviewStage'
import { SessionEndedPanel } from './SessionEndedPanel'
import { InterviewLobby } from './InterviewLobby'
import { InterviewPreparing } from './InterviewPreparing'

export function LiveInterview({ sessionId }: { sessionId: number }) {
  // 음성 자동재생(라이브 세그먼트) 게이팅을 위해 모드를 여기서 소유하고 훅에 넘긴다.
  const [deliveryMode, setDeliveryMode] = useDeliveryMode()
  const {
    session,
    isError,
    refetchSession,
    status,
    items,
    turn,
    connection,
    submitAnswer,
    restoreDraft,
    submitVoice,
    voiceUploading,
    endSession,
    isLoading,
    questionStreaming,
    wasSegmented,
    isSpeaking,
    firstQuestionReady,
  } = useLiveInterview(sessionId, deliveryMode)

  // 에러 분기가 스피너 분기보다 먼저 와야 한다 — 쿼리 실패 시 isLoading 은 false 이고
  // session 은 undefined 라, 순서를 바꾸면 에러가 무한 스피너로 위장된다.
  if (isError) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4 px-6 text-center">
        <p className="text-body font-medium text-fg-strong">세션을 불러오지 못했습니다.</p>
        <p className="text-button font-normal text-fg-muted">
          네트워크 상태를 확인한 뒤 다시 시도해 주세요.
        </p>
        <button
          type="button"
          onClick={refetchSession}
          className="rounded-lg bg-primary px-4 py-2.5 text-button font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
        >
          다시 시도
        </button>
      </div>
    )
  }
  if (isLoading || !session) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner />
      </div>
    )
  }
  if (status === 'READY') {
    return <InterviewLobby sessionId={sessionId} session={session} />
  }
  if (status !== 'IN_PROGRESS') {
    return (
      <SessionEndedPanel
        status={status ?? 'COMPLETED'}
        sessionId={sessionId}
        session={session}
      />
    )
  }
  // 면접은 시작됐지만 첫 질문이 아직 안 왔으면 스테이지 진입 전 대기 화면을 보여준다.
  if (!firstQuestionReady) {
    return <InterviewPreparing session={session} />
  }

  const awaitingQuestion = turn === 'WAITING_FOR_QUESTION'
  return (
    <InterviewStage
      session={session}
      connection={connection}
      items={items}
      awaitingQuestion={awaitingQuestion}
      questionStreaming={questionStreaming}
      onSubmit={submitAnswer}
      restoreDraft={restoreDraft}
      onSubmitVoice={submitVoice}
      voiceUploading={voiceUploading}
      onEnd={endSession}
      wasSegmented={wasSegmented}
      isSpeaking={isSpeaking}
      deliveryMode={deliveryMode}
      onDeliveryModeChange={setDeliveryMode}
    />
  )
}
