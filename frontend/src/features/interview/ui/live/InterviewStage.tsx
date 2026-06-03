import { useState } from 'react'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { Button } from '@/shared/ui/Button'
import { isQuestion, isTranscribing, sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'
import type { ConnectionStatus, ThreadItem } from '../../model/useLiveInterview'
import { ConnectionBanner } from './ConnectionBanner'
import { AnswerComposer } from './AnswerComposer'
import { StageQuestion } from './StageQuestion'
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
            className="h-2.5 w-2.5 animate-pulse rounded-full bg-sage-700/70"
            style={{ animationDelay: `${i * 150}ms` }}
          />
        ))}
      </span>
      <p className="text-body font-medium text-fg">
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
  onSubmit,
  onSubmitVoice,
  voiceUploading,
  onEnd,
}: {
  session: Session
  connection: ConnectionStatus
  items: ThreadItem[]
  awaitingQuestion: boolean
  onSubmit: (content: string) => void
  onSubmitVoice: (audio: Blob) => void
  voiceUploading: boolean
  onEnd: () => void
}) {
  const [transcriptOpen, setTranscriptOpen] = useState(false)
  const progress = sessionProgress(session)
  const currentQuestion = [...items].reverse().find(isQuestion)
  const lastItem = items[items.length - 1]
  const transcribing = Boolean(lastItem && isTranscribing(lastItem))

  return (
    <section className="relative flex h-full flex-col overflow-hidden">
      <div
        aria-hidden
        className="absolute inset-0 bg-cover bg-center"
        style={{ backgroundImage: `url(${BG})` }}
      />
      {/* 배경 사진이 보이도록 옅은 스크림만. 텍스트 가독성은 헤더·질문 카드·
          컴포저가 각자 배경(반투명+blur / solid)으로 확보한다. */}
      <div
        aria-hidden
        className="absolute inset-0 bg-gradient-to-b from-white/30 via-white/10 to-white/45"
      />

      {/* 진행도 바 */}
      <div className="relative z-10 h-1 w-full bg-white/40">
        <div
          className="h-full bg-primary transition-[width] duration-slow ease-standard"
          style={{ width: `${progress.ratio * 100}%` }}
        />
      </div>

      <header className="relative z-10 flex items-center justify-between gap-3 border-b border-white/40 bg-white/55 px-4 py-3 backdrop-blur-md">
        <div className="min-w-0">
          <h1 className="truncate text-h6 text-fg">{session.title ?? '모의 면접'}</h1>
          <p className="text-caption text-fg-muted">
            질문 {progress.current} / {progress.max}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge tone={connTone[connection]}>{connLabel[connection]}</StatusBadge>
          <Button variant="ghost" size="sm" onClick={() => setTranscriptOpen(true)}>
            기록
          </Button>
          <Button variant="danger" size="sm" onClick={onEnd}>
            종료
          </Button>
        </div>
      </header>

      <ConnectionBanner connection={connection} />

      <div className="relative z-10 flex flex-1 items-center justify-center overflow-y-auto px-5 py-8">
        {awaitingQuestion || !currentQuestion ? (
          <ThinkingState transcribing={transcribing} />
        ) : (
          <StageQuestion question={currentQuestion} />
        )}
      </div>

      <div className="relative z-10">
        <AnswerComposer
          disabled={awaitingQuestion || connection !== 'open'}
          onSubmit={onSubmit}
          onSubmitVoice={onSubmitVoice}
          voiceUploading={voiceUploading}
        />
      </div>

      {transcriptOpen && (
        <TranscriptDrawer
          items={items}
          awaitingQuestion={awaitingQuestion}
          onClose={() => setTranscriptOpen(false)}
        />
      )}
    </section>
  )
}
