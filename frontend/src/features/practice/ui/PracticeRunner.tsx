import { useNavigate } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { Spinner } from '@/shared/ui/Spinner'
import { Eyebrow, Heading, StatusBadge } from '@/shared/ui'
import { TextArea } from '@/shared/ui/TextArea'
import type { PracticeTrack } from '@/domain/practice'
import { usePracticeSession } from '../model/usePracticeSession'

const TRACK_LABEL: Record<PracticeTrack, string> = {
  frontend: '프론트엔드 직무 면접',
  backend: '백엔드 직무 면접',
  cs: 'CS 전공 지식 면접',
}

export function PracticeRunner({ track }: { track: PracticeTrack }) {
  const navigate = useNavigate()
  const {
    isLoading,
    isError,
    error,
    refetch,
    current,
    index,
    total,
    isLast,
    done,
    revealed,
    answers,
    reveal,
    next,
    setAnswer,
    restart,
  } = usePracticeSession(track)

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner />
      </div>
    )
  }

  if (isError || total === 0) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <p className="text-body text-fg-muted">
          {error?.message ?? '질문을 불러오지 못했습니다.'}
        </p>
        <Button variant="secondary" onClick={() => refetch()}>
          다시 시도
        </Button>
      </div>
    )
  }

  if (done) {
    return (
      <div className="flex flex-col items-center gap-6 py-20 text-center">
        <div>
          <Heading level="page" as="h2">
            면접을 마쳤습니다 🎉
          </Heading>
          <p className="mt-3 text-body font-normal text-fg-muted">
            총 {total}개의 {TRACK_LABEL[track]} 질문을 연습했습니다.
          </p>
        </div>
        <div className="flex gap-3">
          <Button onClick={restart}>다시 풀기</Button>
          <Button variant="secondary" onClick={() => navigate('/')}>
            홈으로
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-border bg-surface-raised px-5 py-4">
        <div className="min-w-0">
          <h1 className="truncate font-sans text-[18px] font-bold tracking-[-0.02em] text-fg">
            {TRACK_LABEL[track]}
          </h1>
          <p className="font-mono text-caption tracking-tight text-fg-subtle">
            질문 {index + 1} / {total}
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={() => navigate('/')}>
          나가기
        </Button>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-6">
        <div className="mx-auto flex max-w-2xl flex-col gap-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge tone="neutral">{current.subject}</StatusBadge>
            <StatusBadge tone="info">{current.category}</StatusBadge>
          </div>

          <p
            className="font-sans text-[22px] font-bold text-fg sm:text-[26px]"
            style={{ lineHeight: 1.45, letterSpacing: '-0.03em', wordBreak: 'keep-all' }}
          >
            {current.question}
          </p>

          <div className="flex flex-col gap-2">
            <label className="text-caption text-fg-subtle" htmlFor="practice-answer">
              답변 메모 (선택) — 머릿속으로 답하거나 적어본 뒤 모범 답안과 비교해 보세요.
            </label>
            <TextArea
              id="practice-answer"
              value={answers[current.id] ?? ''}
              onChange={(v) => setAnswer(current.id, v)}
              rows={4}
              placeholder="답변을 자유롭게 적어 보세요…"
            />
          </div>

          {revealed ? (
            <div className="rounded-xl border border-border bg-surface px-4 py-4">
              <Eyebrow className="mb-2">모범 답안</Eyebrow>
              <p className="text-body font-normal leading-relaxed text-fg-strong">
                {current.answer}
              </p>
            </div>
          ) : null}
        </div>
      </div>

      <footer className="flex items-center justify-end gap-3 border-t border-border bg-surface-raised px-5 py-4">
        {revealed ? (
          <Button onClick={next}>{isLast ? '면접 종료' : '다음 질문'}</Button>
        ) : (
          <Button variant="secondary" onClick={reveal}>
            모범 답안 보기
          </Button>
        )}
      </footer>
    </div>
  )
}
