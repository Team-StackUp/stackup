import { useMemo } from 'react'
import { Button } from '@/shared/ui/Button'
import { TextArea } from '@/shared/ui/TextArea'
import { Eyebrow, StatusBadge } from '@/shared/ui'
import { useQuestionRunner } from '@/shared/hooks'
import { categoryLabel } from '../lib/categoryLabel'
import type { BookmarkedQuestion } from '../api/bookmarkApi'

const STORAGE_KEY = 'stackup:bookmark-drill-answers'

/**
 * 오답노트 복습 드릴 — 한 문제씩 다시 답해 보고 모범 답안과 비교한다.
 * 연습 면접과 같은 상태 기계(`useQuestionRunner`)를 쓴다.
 */
export function BookmarkDrill({
  items,
  onExit,
}: {
  items: BookmarkedQuestion[]
  onExit: () => void
}) {
  const ids = useMemo(() => items.map((i) => String(i.messageId)), [items])
  const { index, total, isLast, done, revealed, answers, reveal, next, setAnswer, reset } =
    useQuestionRunner(ids, STORAGE_KEY)

  const current = items[index]

  if (done || !current) {
    return (
      <div className="flex flex-col items-center gap-5 py-16 text-center">
        <p className="font-sans text-[20px] font-bold tracking-[-0.02em] text-fg">
          복습을 마쳤습니다
        </p>
        <p className="text-body font-normal text-fg-muted">
          담아둔 질문 {total}개를 모두 다시 풀었어요.
        </p>
        <div className="flex gap-3">
          <Button onClick={reset}>처음부터 다시</Button>
          <Button variant="secondary" onClick={onExit}>
            목록으로
          </Button>
        </div>
      </div>
    )
  }

  const id = String(current.messageId)
  const label = categoryLabel(current.category)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <p className="font-mono text-caption tracking-tight text-fg-subtle">
          {index + 1} / {total}
        </p>
        <Button variant="ghost" size="sm" onClick={onExit}>
          목록으로
        </Button>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {label && <StatusBadge tone="info">{label}</StatusBadge>}
        {current.sessionTitle && (
          <span className="truncate text-caption text-fg-subtle">
            {current.sessionTitle}
          </span>
        )}
      </div>

      <p
        className="font-sans text-[20px] font-bold text-fg sm:text-[22px]"
        style={{ lineHeight: 1.45, letterSpacing: '-0.03em', wordBreak: 'keep-all' }}
      >
        {current.question}
      </p>
      {current.expectedSignal && (
        <p className="text-caption text-fg-muted">평가 관점: {current.expectedSignal}</p>
      )}

      <div className="flex flex-col gap-2">
        <label className="text-caption text-fg-subtle" htmlFor="drill-answer">
          다시 답해 보기 — 적어 본 뒤 그때 답변·모범 답안과 비교하세요.
        </label>
        <TextArea
          id="drill-answer"
          value={answers[id] ?? ''}
          onChange={(v) => setAnswer(id, v)}
          rows={4}
          placeholder="지금이라면 어떻게 답하시겠어요?"
        />
      </div>

      {revealed && (
        <div className="flex flex-col gap-3">
          {current.myAnswer && (
            <Panel title="그때 내 답변" body={current.myAnswer} tone="muted" />
          )}
          {current.modelAnswer && <Panel title="모범 답안" body={current.modelAnswer} />}
          {current.coachingComment && (
            <Panel title="코칭" body={current.coachingComment} tone="muted" />
          )}
          {!current.myAnswer && !current.modelAnswer && !current.coachingComment && (
            <p className="text-caption text-fg-subtle">
              이 질문에는 아직 답변·피드백 기록이 없어요.
            </p>
          )}
        </div>
      )}

      <div className="flex justify-end gap-3 border-t border-border pt-4">
        {revealed ? (
          <Button onClick={next}>{isLast ? '복습 마치기' : '다음 질문'}</Button>
        ) : (
          <Button variant="secondary" onClick={reveal}>
            정답 확인
          </Button>
        )}
      </div>
    </div>
  )
}

function Panel({
  title,
  body,
  tone = 'strong',
}: {
  title: string
  body: string
  tone?: 'strong' | 'muted'
}) {
  return (
    <div className="rounded-lg border border-border bg-surface px-3 py-2.5">
      <Eyebrow className="mb-1">{title}</Eyebrow>
      <p
        className={[
          'whitespace-pre-wrap text-body font-normal leading-relaxed',
          tone === 'strong' ? 'text-fg-strong' : 'text-fg-muted',
        ].join(' ')}
      >
        {body}
      </p>
    </div>
  )
}
