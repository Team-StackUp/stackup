import { useId } from 'react'

import { INDUSTRY_SUGGESTIONS } from '@/domain/session/model/industry'

/**
 * 희망 산업 입력.
 *
 * `datalist` 라서 **제안은 보여주되 목록 밖 값도 그대로 받는다.** 드롭다운(select)으로 막으면
 * 직군이 개발 4종뿐이던 때와 같은 문제가 생긴다 — 빠진 산업의 지원자는 고를 게 없다.
 */
export function IndustryField({
  value,
  onChange,
  label = '희망 산업',
  hint = '목록에 없어도 직접 입력할 수 있어요. 질문이 그 현장의 언어로 나옵니다.',
}: {
  value: string
  onChange: (value: string) => void
  label?: string
  hint?: string
}) {
  const inputId = useId()
  const listId = `${inputId}-industries`
  const hintId = `${inputId}-hint`

  return (
    <div className="space-y-1.5">
      <label htmlFor={inputId} className="block text-[13px] font-semibold text-fg-muted">
        {label}
      </label>
      <input
        id={inputId}
        list={listId}
        value={value}
        maxLength={100}
        onChange={(e) => onChange(e.target.value)}
        placeholder="예: 반도체, 건설·토목, 금융"
        aria-describedby={hintId}
        className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-body text-fg placeholder:text-fg-subtle focus:border-brand focus:outline-none"
      />
      <datalist id={listId}>
        {INDUSTRY_SUGGESTIONS.map((s) => (
          <option key={s} value={s} />
        ))}
      </datalist>
      <p id={hintId} className="text-caption text-fg-subtle">
        {hint}
      </p>
    </div>
  )
}
