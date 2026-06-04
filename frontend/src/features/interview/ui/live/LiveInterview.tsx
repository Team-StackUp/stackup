import { Spinner } from '@/shared/ui/Spinner'
import { useLiveInterview } from '../../model/useLiveInterview'
import { InterviewStage } from './InterviewStage'
import { SessionEndedPanel } from './SessionEndedPanel'
import { InterviewLobby } from './InterviewLobby'

export function LiveInterview({ sessionId }: { sessionId: number }) {
  const {
    session,
    status,
    items,
    turn,
    connection,
    submitAnswer,
    submitVoice,
    voiceUploading,
    endSession,
    isLoading,
    questionStreaming,
    wasSegmented,
  } = useLiveInterview(sessionId)

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
    return <SessionEndedPanel status={status ?? 'COMPLETED'} sessionId={sessionId} />
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
      onSubmitVoice={submitVoice}
      voiceUploading={voiceUploading}
      onEnd={endSession}
      wasSegmented={wasSegmented}
    />
  )
}
