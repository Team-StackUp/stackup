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
  return (
    <div role="group" aria-label={ariaLabel} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {options.map((opt) => {
        const selected = value.includes(opt.value)
        return (
          <button
            type="button"
            key={opt.value}
            role="checkbox"
            aria-checked={selected}
            onClick={() => onToggle(opt.value)}
            className={`flex flex-col gap-1 rounded-lg border px-3 py-3 text-left transition-colors ${
              selected
                ? 'border-primary bg-surface'
                : 'border-border bg-surface-raised hover:border-border-strong'
            }`}
          >
            <span className="text-button text-fg">{opt.label}</span>
            {opt.description ? (
              <span className="text-caption text-fg-muted">{opt.description}</span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
