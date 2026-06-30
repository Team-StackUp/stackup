import { Button } from '../Button'

export type StepperProps = {
  value: number
  onChange: (value: number) => void
  min?: number
  max?: number
  step?: number
  ariaLabel: string
}

export function Stepper({ value, onChange, min = 0, max = 100, step = 1, ariaLabel }: StepperProps) {
  const clamp = (n: number) => Math.max(min, Math.min(max, n))
  return (
    <div className="inline-flex items-center gap-3" role="group" aria-label={ariaLabel}>
      <Button
        variant="secondary"
        size="sm"
        type="button"
        aria-label="감소"
        className="min-h-11 min-w-11"
        disabled={value <= min}
        onClick={() => onChange(clamp(value - step))}
      >
        −
      </Button>
      <span className="min-w-8 text-center text-body text-fg">{value}</span>
      <Button
        variant="secondary"
        size="sm"
        type="button"
        aria-label="증가"
        className="min-h-11 min-w-11"
        disabled={value >= max}
        onClick={() => onChange(clamp(value + step))}
      >
        +
      </Button>
    </div>
  )
}
