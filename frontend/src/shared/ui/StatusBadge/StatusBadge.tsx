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
      // 뱃지는 절대 줄바꿈되지 않아야 한다 — 좁은 화면에서 '재연결 중'이 두 줄로 쪼개져
      // 알약 모양이 깨지는 문제가 있었다.
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-pill px-2.5 py-0.5 text-caption font-medium ${toneClass[tone]}`}
    >
      {children}
    </span>
  )
}
