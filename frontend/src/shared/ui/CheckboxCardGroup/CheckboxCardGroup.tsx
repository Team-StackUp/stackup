import { useId } from 'react'

export type CheckboxOption<T extends string> = {
  value: T
  label: string
  description?: string
}

export type CheckboxCardGroupProps<T extends string> = {
  options: CheckboxOption<T>[]
  value: T[]
  onToggle: (value: T) => void
  ariaLabel: string
}

// 다중 선택 카드 그룹. RadioCardGroup 의 다중 선택 버전.
export function CheckboxCardGroup<T extends string>({
  options,
  value,
  onToggle,
  ariaLabel,
}: CheckboxCardGroupProps<T>) {
  // 이름 계산은 RadioCardGroup 과 동일 규칙 — 제목만 이름, 설명은 describedby.
  const baseId = useId()
  return (
    <div role="group" aria-label={ariaLabel} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {options.map((opt) => {
        const selected = value.includes(opt.value)
        const labelId = `${baseId}-${opt.value}-label`
        const descId = `${baseId}-${opt.value}-desc`
        return (
          <button
            type="button"
            key={opt.value}
            role="checkbox"
            aria-checked={selected}
            aria-labelledby={labelId}
            aria-describedby={opt.description ? descId : undefined}
            onClick={() => onToggle(opt.value)}
            className={`flex flex-col gap-1 rounded-xl border px-3.5 py-3 text-left transition-colors duration-fast ${
              selected
                ? 'border-primary bg-primary-50'
                : 'border-border bg-surface-raised hover:border-border-strong'
            }`}
          >
            <span
              id={labelId}
              className={`text-button font-semibold ${selected ? 'text-primary-fg' : 'text-fg'}`}
            >
              {opt.label}
            </span>
            {opt.description ? (
              <span id={descId} className="text-caption text-fg-muted">
                {opt.description}
              </span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
