export type RadioOption<T extends string> = { value: T; label: string; description?: string }

export type RadioCardGroupProps<T extends string> = {
  options: RadioOption<T>[]
  value: T | null
  onChange: (value: T) => void
  ariaLabel: string
}

export function RadioCardGroup<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: RadioCardGroupProps<T>) {
  return (
    <div role="radiogroup" aria-label={ariaLabel} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {options.map((opt) => {
        const selected = opt.value === value
        return (
          <button
            type="button"
            key={opt.value}
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(opt.value)}
            className={`flex flex-col gap-1 rounded-lg border px-3 py-3 text-left transition-colors ${
              selected
                ? 'border-primary bg-surface'
                : 'border-border bg-surface-raised hover:border-border-strong'
            }`}
          >
            <span className="text-button text-fg">{opt.label}</span>
            {opt.description ? <span className="text-caption text-fg-muted">{opt.description}</span> : null}
          </button>
        )
      })}
    </div>
  )
}
