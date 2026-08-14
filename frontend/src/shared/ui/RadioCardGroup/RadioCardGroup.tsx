import { useId, useRef } from 'react'

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
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  // radiogroup 은 Tab 이 아니라 화살표로 항목 사이를 이동하고, 그룹 전체가 탭 정지점
  // 하나를 갖는 패턴(roving tabindex)이다. role 만 선언하고 키 처리를 빼면 스크린리더
  // 사용자가 기대한 대로 조작할 수 없다.
  const moveFocus = (from: number, delta: number) => {
    const next = (from + delta + options.length) % options.length
    onChange(options[next].value)
    refs.current[next]?.focus()
  }

  const onKeyDown = (event: React.KeyboardEvent, index: number) => {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key]
    if (step === undefined) return
    event.preventDefault()
    moveFocus(index, step)
  }

  // 선택 항목이 탭 정지점. 아직 선택 전이면 첫 항목이 받는다.
  const focusableIndex = Math.max(
    options.findIndex((opt) => opt.value === value),
    0,
  )

  return (
    <div role="radiogroup" aria-label={ariaLabel} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {options.map((opt, index) => {
        const selected = opt.value === value
        const labelId = `${baseId}-${opt.value}-label`
        const descId = `${baseId}-${opt.value}-desc`
        return (
          <button
            type="button"
            key={opt.value}
            ref={(el) => {
              refs.current[index] = el
            }}
            role="radio"
            aria-checked={selected}
            tabIndex={index === focusableIndex ? 0 : -1}
            onKeyDown={(event) => onKeyDown(event, index)}
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
