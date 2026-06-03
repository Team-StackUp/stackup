import { Spinner } from '@/shared/ui/Spinner'
import { useLiveInterview } from '../../model/useLiveInterview'
import { InterviewHeader } from './InterviewHeader'
import { ConnectionBanner } from './ConnectionBanner'
import { ConversationThread } from './ConversationThread'
import { AnswerComposer } from './AnswerComposer'
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
  } = useLiveInterview(sessionId)

  if (isLoading || !session) {
    return (
      <div className="flex justify-center py-16">
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
    <div className="flex h-full flex-col">
      <InterviewHeader session={session} connection={connection} onEnd={endSession} />
      <ConnectionBanner connection={connection} />
      <div className="min-h-0 flex-1">
        <ConversationThread items={items} awaitingQuestion={awaitingQuestion} />
      </div>
      <AnswerComposer
        disabled={turn !== 'AWAITING_ANSWER' || connection !== 'open'}
        onSubmit={submitAnswer}
        onSubmitVoice={submitVoice}
        voiceUploading={voiceUploading}
      />
    </div>
  )
}
