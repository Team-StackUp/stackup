import type { ReactElement } from 'react'
import type { DeliveryMode } from '../../model/useDeliveryMode'

function TextIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
      <path d="M4 7V5h16v2M9 5v14M9 19h6" />
    </svg>
  )
}

function VoiceIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z" />
      <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
    </svg>
  )
}

const options: { value: DeliveryMode; label: string; icon: () => ReactElement }[] = [
  { value: 'text', label: '텍스트', icon: TextIcon },
  { value: 'voice', label: '음성', icon: VoiceIcon },
]

// 면접 진행 방식(텍스트/음성)을 고르는 컴팩트 세그먼트 토글.
export function DeliveryModeToggle({
  value,
  onChange,
}: {
  value: DeliveryMode
  onChange: (mode: DeliveryMode) => void
}) {
  return (
    <div
      role="radiogroup"
      aria-label="면접 진행 방식"
      className="inline-flex items-center gap-0.5 rounded-pill border border-white/50 bg-white/55 p-0.5 backdrop-blur-md"
    >
      {options.map(({ value: v, label, icon: Icon }) => {
        const active = value === v
        return (
          <button
            key={v}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(v)}
            className={[
              'inline-flex items-center gap-1.5 rounded-pill px-2.5 py-1 text-caption font-medium transition-colors',
              active
                ? 'bg-sage-800 text-white shadow-sm'
                : 'text-fg-muted hover:text-fg',
            ].join(' ')}
          >
            <Icon />
            <span className="hidden sm:inline">{label}</span>
          </button>
        )
      })}
    </div>
  )
}
