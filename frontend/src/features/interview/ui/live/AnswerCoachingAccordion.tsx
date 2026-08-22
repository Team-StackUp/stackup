import { useState } from 'react'
import type { Message } from '@/domain/session'
import { Markdown } from '@/shared/ui'

// 답변 구조 채점값(FULL_STAR/PARTIAL_STAR/NONE) → 한국어 라벨.
const STRUCTURE_LABEL: Record<string, string> = {
  FULL_STAR: '구조 양호',
  PARTIAL_STAR: '구조 보통',
  NONE: '구조 미흡',
}

function score5(v?: number | null): string | null {
  return typeof v === 'number' ? `${v}/5` : null
}

// 전달력 배지(결정론적 평가) → 라벨 + 색상 토큰.
const DELIVERY_RATING: Record<string, { label: string; cls: string }> = {
  GOOD: { label: '전달력 우수', cls: 'bg-success-50 text-success-700' },
  FAIR: { label: '전달력 보통', cls: 'bg-warning-50 text-warning-700' },
  POOR: { label: '전달력 미흡', cls: 'bg-danger-50 text-danger-700' },
}

// 답변 음성 전달력 메트릭 → 표시용 칩 문자열들.
function deliveryChips(message: Message): string[] {
  const chips: string[] = []
  if (typeof message.speakingRateWpm === 'number') {
    chips.push(`${Math.round(message.speakingRateWpm)} WPM`)
  }
  if (typeof message.silenceDurationSec === 'number' && message.silenceDurationSec > 0) {
    chips.push(`무음 ${Math.round(message.silenceDurationSec)}초`)
  }
  const fillers = Object.entries(message.fillerWordCounts ?? {}).filter(([, c]) => c > 0)
  if (fillers.length > 0) {
    chips.push('간투어 ' + fillers.map(([w, c]) => `${w} ${c}`).join(' · '))
  }
  if (typeof message.pronunciationAccuracy === 'number') {
    chips.push(`발음 정확도 ${Math.round(message.pronunciationAccuracy * 100)}%`)
  }
  return chips
}

// 종료된 세션의 답변 아래 붙는 '복기' 아코디언: 전달력 메트릭 → 평가 점수 → 한 줄 코칭 → 모범 답안 → 리라이트.
// 코칭·전달력 데이터가 모두 없으면(라이브 중·자기소개 답변 등) 렌더하지 않는다.
export function AnswerCoachingAccordion({ message }: { message: Message }) {
  const [open, setOpen] = useState(false)
  const delivery = deliveryChips(message)
  const rating = message.deliveryRating ? DELIVERY_RATING[message.deliveryRating] : undefined
  const hasContent = Boolean(
    message.modelAnswer ||
      message.answerRewrite ||
      message.coachingComment ||
      message.deliveryComment ||
      delivery.length > 0,
  )
  if (!hasContent) return null

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
          {(delivery.length > 0 || rating || message.deliveryComment) && (
            <div className="flex flex-col gap-1">
              <div className="flex items-center gap-2">
                <span className="text-caption text-fg-subtle">전달력</span>
                {rating && (
                  <span
                    className={`rounded-pill px-2 py-0.5 text-caption font-medium ${rating.cls}`}
                  >
                    {rating.label}
                  </span>
                )}
              </div>
              {delivery.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {delivery.map((d) => (
                    <span
                      key={d}
                      className="rounded-pill bg-info-50 px-2 py-0.5 text-caption text-info-700"
                    >
                      {d}
                    </span>
                  ))}
                </div>
              )}
              {message.deliveryComment && (
                <p className="text-caption text-fg-muted">{message.deliveryComment}</p>
              )}
            </div>
          )}
          {evals.length > 0 && (
            <div className="flex flex-col gap-1">
              <span className="text-caption text-fg-subtle">답변 평가</span>
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
            </div>
          )}
          {message.coachingComment && (
            <p className="text-body text-fg">
              <span className="text-primary-fg">코칭 · </span>
              {message.coachingComment}
            </p>
          )}
          {message.modelAnswer && (
            <div className="flex flex-col gap-1">
              <span className="text-caption text-fg-subtle">모범 답안</span>
              {/* 모범 답안·리라이트는 GFM 마크다운 계약 필드 — 코드·리스트가 실제로 온다
                  (docs/messaging.md §5.11). 나머지 코칭 텍스트는 plain text 계약. */}
              <Markdown className="text-fg-muted">{message.modelAnswer}</Markdown>
            </div>
          )}
          {message.answerRewrite && (
            <div className="flex flex-col gap-1">
              <span className="text-caption text-fg-subtle">내 답변, 이렇게 고치면</span>
              <Markdown className="text-fg-muted">{message.answerRewrite}</Markdown>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
