import { useState } from 'react'
import { Spinner } from '@/shared/ui/Spinner'
import { useCreateCoverLetter } from '../model/useCoverLetters'

const TITLE_MAX = 200
const ANSWER_MAX = 5000

type DraftItem = { question: string; answer: string }

const emptyItem = (): DraftItem => ({ question: '', answer: '' })

// 공채 자소서 문항별 입력 — 질문(문항) + 답변을 여러 개 추가. 텍스트 전용.
export function CoverLetterForm() {
  const [title, setTitle] = useState('')
  const [items, setItems] = useState<DraftItem[]>([emptyItem()])
  const create = useCreateCoverLetter()

  const updateItem = (idx: number, patch: Partial<DraftItem>) =>
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, ...patch } : it)))
  const addItem = () => setItems((prev) => [...prev, emptyItem()])
  const removeItem = (idx: number) =>
    setItems((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== idx)))

  // 답변이 하나라도 채워져 있어야 제출 가능.
  const hasAnswer = items.some((it) => it.answer.trim().length > 0)
  const submitting = create.isPending

  const handleSubmit = () => {
    if (!hasAnswer || submitting) return
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
    <div className="flex flex-col gap-5 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
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
          className="rounded-lg border border-border bg-surface px-3 py-2 text-body text-fg outline-none focus:border-primary"
        />
      </div>

      <div className="flex flex-col gap-4">
        {items.map((item, idx) => (
          <div key={idx} className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-4">
            <div className="flex items-center justify-between gap-2">
              <span className="text-caption font-semibold text-fg-subtle">문항 {idx + 1}</span>
              {items.length > 1 ? (
                <button
                  type="button"
                  onClick={() => removeItem(idx)}
                  className="rounded-md px-2 py-0.5 text-caption text-fg-subtle transition-colors hover:bg-surface-raised hover:text-danger-700"
                >
                  삭제
                </button>
              ) : null}
            </div>
            <input
              type="text"
              value={item.question}
              placeholder="문항 (예: 지원 동기와 입사 후 포부를 기술해 주세요)"
              onChange={(e) => updateItem(idx, { question: e.target.value })}
              className="rounded-lg border border-border bg-surface-raised px-3 py-2 text-body text-fg outline-none focus:border-primary"
            />
            <textarea
              value={item.answer}
              rows={5}
              maxLength={ANSWER_MAX}
              placeholder="답변을 입력하세요"
              onChange={(e) => updateItem(idx, { answer: e.target.value })}
              className="resize-y rounded-lg border border-border bg-surface-raised px-3 py-2 text-body text-fg outline-none focus:border-primary"
            />
            <span className="self-end text-caption text-fg-muted">
              {item.answer.length} / {ANSWER_MAX}
            </span>
          </div>
        ))}
      </div>

      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={addItem}
          className="rounded-lg border border-dashed border-border-strong px-4 py-2 text-button text-fg-strong/80 transition-colors hover:border-primary hover:text-fg-strong"
        >
          + 문항 추가
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={!hasAnswer || submitting}
          aria-busy={submitting}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2 text-button text-fg-on-primary transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? <Spinner /> : null}
          {submitting ? '저장 중…' : '자소서 저장'}
        </button>
      </div>
    </div>
  )
}
