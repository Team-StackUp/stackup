import { useState } from 'react'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { focusAreaLabel } from '../../lib/focusArea'
import { Button } from '@/shared/ui/Button'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { isQuestion, isTranscribing, sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'
import type { ConnectionStatus, ThreadItem } from '../../model/useLiveInterview'
import type { DeliveryMode } from '../../model/useDeliveryMode'
import { ConnectionBanner } from '@/shared/ui'
import { SmallScreenNotice } from './SmallScreenNotice'
import { AnswerComposer } from './AnswerComposer'
import { StageQuestion } from './StageQuestion'
import { DeliveryModeToggle } from './DeliveryModeToggle'
import { InterviewerAvatar } from './InterviewerAvatar'
import { WebcamSelfView } from './WebcamSelfView'
import { TranscriptDrawer } from './TranscriptDrawer'

const BG = '/interview-session-background.png'

const connTone: Record<ConnectionStatus, 'success' | 'warning' | 'danger'> = {
  open: 'success',
  connecting: 'warning',
  closed: 'danger',
}
const connLabel: Record<ConnectionStatus, string> = {
  open: '연결됨',
  connecting: '연결 중',
  closed: '재연결 중',
}

function ThinkingState({ transcribing }: { transcribing: boolean }) {
  return (
    <div className="flex flex-col items-center gap-4 text-fg-muted">
      <span className="flex gap-1.5" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-2.5 w-2.5 animate-pulse rounded-full bg-fg-subtle"
            style={{ animationDelay: `${i * 150}ms` }}
          />
        ))}
      </span>
      <p className="text-body font-medium text-fg" role="status">
        {transcribing ? '답변을 받아 적고 있어요…' : '다음 질문을 준비하고 있어요…'}
      </p>
    </div>
  )
}

export function InterviewStage({
  session,
  connection,
  items,
  awaitingQuestion,
  questionStreaming,
  onSubmit,
  restoreDraft,
  onSubmitVoice,
  voiceUploading,
  onEnd,
  onInterrupt,
  wasSegmented,
  isSpeaking,
  deliveryMode,
  onDeliveryModeChange,
}: {
  session: Session
  connection: ConnectionStatus
  items: ThreadItem[]
  awaitingQuestion: boolean
  questionStreaming: boolean
  onSubmit: (content: string) => void
  restoreDraft?: { content: string; nonce: number } | null
  onSubmitVoice: (audio: Blob) => void
  voiceUploading: boolean
  onEnd: () => void
  /** 잠시 중단 — 대화를 남긴 채 나중에 이어서 진행한다. */
  onInterrupt: () => void
  wasSegmented: (id: number) => boolean
  isSpeaking: (id: number) => boolean
  deliveryMode: DeliveryMode
  onDeliveryModeChange: (mode: DeliveryMode) => void
}) {
  const [transcriptOpen, setTranscriptOpen] = useState(false)
  const [endConfirmOpen, setEndConfirmOpen] = useState(false)
  const [pauseConfirmOpen, setPauseConfirmOpen] = useState(false)
  const progress = sessionProgress(session)
  const focusAreas = session.focusAreas ?? []
  const currentQuestion = [...items].reverse().find(isQuestion)
  const lastItem = items[items.length - 1]
  const transcribing = Boolean(lastItem && isTranscribing(lastItem))
  const speaking = currentQuestion ? isSpeaking(currentQuestion.id ?? -1) : false

  return (
    <section className="anim-screen-power-on relative flex h-full flex-col overflow-hidden">
      <div
        aria-hidden
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: `url(${BG})` }}
      />
      {/* 배경 사진이 보이도록 옅은 스크림만. 텍스트 가독성은 헤더·질문 카드·
          컴포저가 각자 배경(반투명+blur / solid)으로 확보한다. */}
      <div
        aria-hidden
        className="absolute inset-0 bg-gradient-to-b from-surface-raised/30 via-surface-raised/10 to-surface-raised/45"
      />

      {/* 진행도 바 */}
      <div className="relative z-10 h-1 w-full bg-surface-raised/40">
        <div
          className="h-full bg-primary transition-[width] duration-slow ease-standard"
          style={{ width: `${progress.ratio * 100}%` }}
        />
      </div>

      <header className="relative z-10 flex items-center justify-between gap-3 border-b border-surface-raised/40 bg-surface-raised/55 px-4 py-3 backdrop-blur-md">
        <div className="min-w-0">
          <h1 className="truncate font-sans text-[18px] font-bold tracking-[-0.02em] text-fg">
            {session.title ?? '모의 면접'}
          </h1>
          <p className="font-mono text-caption tracking-tight text-fg-subtle">
            질문 {progress.current} / {progress.max}
            {/* 약점 집중 면접이면 무엇을 겨냥하는지 보여준다 — 질문 성격이 달라진 이유다. */}
            {focusAreas.length > 0 && (
              <span className="ml-2 font-sans text-caption text-primary-fg">
                {focusAreas.map(focusAreaLabel).join('·')} 집중
              </span>
            )}
          </p>
        </div>
        {/* shrink-0 이 없으면 좁은 화면에서 버튼 글자가 눌려 읽히지 않는다.
            연결 배지는 좁은 화면에서 숨긴다 — ConnectionBanner 가 같은 내용을 이미 알린다. */}
        <div className="flex shrink-0 items-center gap-2">
          <DeliveryModeToggle value={deliveryMode} onChange={onDeliveryModeChange} />
          <span className="hidden sm:inline">
            <StatusBadge tone={connTone[connection]}>{connLabel[connection]}</StatusBadge>
          </span>
          <Button variant="ghost" size="sm" onClick={() => setTranscriptOpen(true)}>
            기록
          </Button>
          {/* 종료(되돌릴 수 없음, 즉시 피드백)만 있으면 잠깐 자리를 비워야 할 때 방법이 없다. */}
          <Button variant="ghost" size="sm" onClick={() => setPauseConfirmOpen(true)}>
            잠시 중단
          </Button>
          <Button variant="danger" size="sm" onClick={() => setEndConfirmOpen(true)}>
            종료
          </Button>
        </div>
      </header>

      <SmallScreenNotice />
      <ConnectionBanner
        connection={connection}
        connectingText="면접 서버에 연결 중입니다…"
        reconnectingText="연결이 끊겨 재연결 중입니다…"
      />

      <div className="relative z-10 flex flex-1 flex-col items-center justify-center gap-6 overflow-y-auto px-5 py-8">
        <InterviewerAvatar
          state={
            awaitingQuestion || !currentQuestion
              ? 'thinking'
              : speaking
                ? 'speaking'
                : 'asking'
          }
        />
        {awaitingQuestion || !currentQuestion ? (
          <ThinkingState transcribing={transcribing} />
        ) : (
          <StageQuestion
            question={currentQuestion}
            segmented={wasSegmented(currentQuestion.id ?? -1)}
            speaking={speaking}
            streaming={currentQuestion?.streaming ?? false}
            mode={deliveryMode}
          />
        )}
      </div>

      <div className="absolute bottom-24 right-4 z-20 sm:bottom-28">
        <WebcamSelfView />
      </div>

      <div className="relative z-10">
        <AnswerComposer
          disabled={awaitingQuestion || connection !== 'open'}
          disabledReason={
            connection !== 'open' && !awaitingQuestion ? 'disconnected' : 'awaiting-question'
          }
          submitLocked={questionStreaming}
          onSubmit={onSubmit}
          restoreDraft={restoreDraft}
          onSubmitVoice={onSubmitVoice}
          voiceUploading={voiceUploading}
        />
      </div>

      {transcriptOpen && (
        <TranscriptDrawer
          items={items}
          awaitingQuestion={awaitingQuestion}
          mode={deliveryMode}
          onClose={() => setTranscriptOpen(false)}
        />
      )}

      <ConfirmDialog
        open={pauseConfirmOpen}
        title="면접을 잠시 중단할까요?"
        description="지금까지 주고받은 대화는 그대로 남습니다. 바로 다음 화면에서 이어서 진행할 수 있고, 나중에 히스토리에서도 다시 들어올 수 있어요. 피드백은 아직 만들어지지 않습니다."
        confirmLabel="중단하기"
        cancelLabel="계속 진행"
        onConfirm={() => {
          setPauseConfirmOpen(false)
          onInterrupt()
        }}
        onCancel={() => setPauseConfirmOpen(false)}
      />

      <ConfirmDialog
        open={endConfirmOpen}
        title="면접을 종료하시겠습니까?"
        description="진행 중인 면접이 끝나고 피드백 단계로 넘어갑니다. 이 작업은 되돌릴 수 없습니다."
        confirmLabel="종료"
        cancelLabel="계속 진행"
        danger
        onConfirm={() => {
          setEndConfirmOpen(false)
          onEnd()
        }}
        onCancel={() => setEndConfirmOpen(false)}
      />
    </section>
  )
}
