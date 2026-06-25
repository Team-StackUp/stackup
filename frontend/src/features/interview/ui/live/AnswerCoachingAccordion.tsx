import { useState } from 'react'
import type { Message } from '@/domain/session'

// 답변 구조 채점값(FULL_STAR/PARTIAL_STAR/NONE) → 한국어 라벨.
const STRUCTURE_LABEL: Record<string, string> = {
  FULL_STAR: '구조 양호',
  PARTIAL_STAR: '구조 보통',
  NONE: '구조 미흡',
}

function score5(v?: number | null): string | null {
  return typeof v === 'number' ? `${v}/5` : null
}

// 종료된 세션의 답변 아래 붙는 '복기' 아코디언: 평가 점수 → 한 줄 코칭 → 모범 답안 → 내 답변 리라이트.
// 코칭 데이터가 없으면(라이브 중·자기소개 답변 등) 렌더하지 않는다.
export function AnswerCoachingAccordion({ message }: { message: Message }) {
  const [open, setOpen] = useState(false)
  const hasCoaching = Boolean(
    message.modelAnswer || message.answerRewrite || message.coachingComment,
  )
  if (!hasCoaching) return null

  const spec = score5(message.answerSpecificity)
  const logic = score5(message.answerLogic)
  const correctness = score5(message.answerCorrectness)
  const evals = [
    spec && `구체성 ${spec}`,
    logic && `논리 ${logic}`,
    correctness && `정답성 ${correctness}`,
    message.answerStructure
      ? (STRUCTURE_LABEL[message.answerStructure] ?? message.answerStructure)
      : null,
  ].filter(Boolean) as string[]

  return (
    <div className="w-full max-w-[80%] self-end">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="text-caption text-fg-muted transition-colors hover:text-fg"
      >
        {open ? '복기 닫기 ▴' : '복기 보기 ▾'}
      </button>
      {open && (
        <div className="mt-1.5 flex flex-col gap-3 rounded-lg border border-border bg-surface px-4 py-3 text-left">
          {evals.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {evals.map((e) => (
                <span
                  key={e}
                  className="rounded-pill bg-surface-raised px-2 py-0.5 text-caption text-fg-muted"
                >
                  {e}
                </span>
              ))}
            </div>
          )}
          {message.coachingComment && (
            <p className="text-body text-fg">
              <span className="text-primary">코칭 · </span>
              {message.coachingComment}
            </p>
          )}
          {message.modelAnswer && (
            <div className="flex flex-col gap-1">
              <span className="text-caption text-fg-subtle">모범 답안</span>
              <p className="whitespace-pre-wrap text-body text-fg-muted">
                {message.modelAnswer}
              </p>
            </div>
          )}
          {message.answerRewrite && (
            <div className="flex flex-col gap-1">
              <span className="text-caption text-fg-subtle">내 답변, 이렇게 고치면</span>
              <p className="whitespace-pre-wrap text-body text-fg-muted">
                {message.answerRewrite}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
