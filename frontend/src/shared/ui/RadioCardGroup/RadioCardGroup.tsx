import { useId } from 'react'

export type RadioOption<T extends string> = { value: T; label: string; description?: string }

export type RadioCardGroupProps<T extends string> = {
  options: RadioOption<T>[]
  value: T | null
  onChange: (value: T) => void
  ariaLabel: string
}

// 카드의 접근성 이름은 제목만이어야 한다. 그냥 두면 설명까지 합쳐져
// "기술 면접실무 기술·CS 위주" 가 이름이 되고, 스크린리더가 항목을 읽을 때마다 설명을
// 되풀이한다. 제목은 aria-labelledby, 설명은 aria-describedby 로 분리한다.
export function RadioCardGroup<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: RadioCardGroupProps<T>) {
  const baseId = useId()
  return (
    <div role="radiogroup" aria-label={ariaLabel} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {options.map((opt) => {
        const selected = opt.value === value
        const labelId = `${baseId}-${opt.value}-label`
        const descId = `${baseId}-${opt.value}-desc`
        return (
          <button
            type="button"
            key={opt.value}
            role="radio"
            aria-checked={selected}
            aria-labelledby={labelId}
            aria-describedby={opt.description ? descId : undefined}
            onClick={() => onChange(opt.value)}
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
