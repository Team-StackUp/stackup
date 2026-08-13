import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { useCreateCoverLetter } from '../model/useCoverLetters'

const TITLE_MAX = 200
const ANSWER_MAX = 5000

type DraftItem = { id: string; question: string; answer: string }

const emptyItem = (): DraftItem => ({ id: crypto.randomUUID(), question: '', answer: '' })

const FIELD_FOCUS =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--color-primary)] focus-visible:border-primary'

// 공채 자소서 문항별 입력 — 질문(문항) + 답변을 여러 개 추가. 텍스트 전용.
export function CoverLetterForm() {
  const [title, setTitle] = useState('')
  const [items, setItems] = useState<DraftItem[]>([emptyItem()])
  const create = useCreateCoverLetter()

  const updateItem = (id: string, patch: Partial<DraftItem>) =>
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)))
  const addItem = () => setItems((prev) => [...prev, emptyItem()])
  const removeItem = (id: string) =>
    setItems((prev) => (prev.length <= 1 ? prev : prev.filter((it) => it.id !== id)))

  // 답변이 하나라도 채워져 있어야 제출 가능.
  const hasAnswer = items.some((it) => it.answer.trim().length > 0)
  // 질문은 적었는데 답변이 빈 문항 — 무음 drop 대신 경고 + 제출 차단.
  const isIncomplete = (it: DraftItem) =>
    it.question.trim().length > 0 && it.answer.trim().length === 0
  const hasIncomplete = items.some(isIncomplete)
  const submitting = create.isPending
  const canSubmit = hasAnswer && !hasIncomplete && !submitting

  const handleSubmit = () => {
    if (!canSubmit) return
    const payloadItems = items
      .filter((it) => it.answer.trim().length > 0)
      .map((it) => ({ question: it.question.trim(), answer: it.answer.trim() }))
    create.mutate(
      { title: title.trim() || undefined, items: payloadItems },
      {
        onSuccess: () => {
          setTitle('')
          setItems([emptyItem()])
        },
      },
    )
  }

  return (
    <div className="flex flex-col gap-5 rounded-xl border border-border bg-surface-raised p-5">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="cl-title" className="text-body font-semibold text-fg-strong">
          제목 <span className="text-caption font-normal text-fg-muted">(선택)</span>
        </label>
        <input
          id="cl-title"
          type="text"
          value={title}
          maxLength={TITLE_MAX}
          placeholder="예: OO기업 2026 상반기 공채 자소서"
          onChange={(e) => setTitle(e.target.value)}
          className={`rounded-lg border border-border bg-surface px-3 py-2 text-body text-fg ${FIELD_FOCUS}`}
        />
      </div>

      <div className="flex flex-col gap-4">
        {items.map((item, idx) => (
          <div
            key={item.id}
            className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-4"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-caption font-semibold text-fg-subtle">문항 {idx + 1}</span>
              {items.length > 1 ? (
                <button
                  type="button"
                  onClick={() => removeItem(item.id)}
                  aria-label={`문항 ${idx + 1} 삭제`}
                  className="rounded-md px-2 py-0.5 text-caption text-fg-subtle transition-colors hover:bg-surface-raised hover:text-danger-700"
                >
                  삭제
                </button>
              ) : null}
            </div>
            <input
              type="text"
              value={item.question}
              aria-label={`문항 ${idx + 1} 질문`}
              placeholder="문항 (예: 지원 동기와 입사 후 포부를 기술해 주세요)"
              onChange={(e) => updateItem(item.id, { question: e.target.value })}
              className={`rounded-lg border border-border bg-surface-raised px-3 py-2 text-body text-fg ${FIELD_FOCUS}`}
            />
            <textarea
              value={item.answer}
              rows={5}
              maxLength={ANSWER_MAX}
              aria-label={`문항 ${idx + 1} 답변`}
              placeholder="답변을 입력하세요"
              onChange={(e) => updateItem(item.id, { answer: e.target.value })}
              className={`resize-y rounded-lg border border-border bg-surface-raised px-3 py-2 text-body text-fg ${FIELD_FOCUS}`}
            />
            <div className="flex items-center justify-between gap-2">
              {isIncomplete(item) ? (
                <span className="text-caption text-danger-700">답변을 입력하거나 문항을 비워 주세요.</span>
              ) : (
                <span />
              )}
              <span className="text-caption text-fg-muted">
                {item.answer.length} / {ANSWER_MAX}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={addItem}
          className="rounded-lg border border-dashed border-border-strong px-4 py-2 text-button font-medium text-fg-muted transition-colors duration-fast hover:border-primary hover:text-fg-strong"
        >
          + 문항 추가
        </button>
        <Button
          type="button"
          variant="primary"
          onClick={handleSubmit}
          disabled={!canSubmit}
          loading={submitting}
        >
          {submitting ? '저장 중…' : '자소서 저장'}
        </Button>
      </div>
    </div>
  )
}
