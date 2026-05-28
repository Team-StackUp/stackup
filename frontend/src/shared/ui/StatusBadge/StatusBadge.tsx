import type { ReactNode } from 'react'

export type StatusTone =
  | 'neutral'
  | 'info'
  | 'warning'
  | 'success'
  | 'danger'

const toneClass: Record<StatusTone, string> = {
  neutral: 'bg-surface text-fg-muted',
  info: 'bg-info-50 text-info-700',
  warning: 'bg-warning-50 text-warning-700',
  success: 'bg-success-50 text-success-700',
  danger: 'bg-danger-50 text-danger-700',
}

export type StatusBadgeProps = {
  tone?: StatusTone
  children: ReactNode
}

export function StatusBadge({ tone = 'neutral', children }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-pill px-2.5 py-0.5 text-caption font-medium ${toneClass[tone]}`}
    >
      {children}
    </span>
  )
}
