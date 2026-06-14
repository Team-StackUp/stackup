import type { ReactNode } from 'react'

export type EmptyStateProps = {
  title: ReactNode
  description?: ReactNode
  icon?: ReactNode
  // CTA 등 액션 영역. 라우팅/도메인 의존을 피하려 호출부에서 ReactNode 로 주입한다.
  action?: ReactNode
  className?: string
}

// 목록/결과가 비었을 때의 표준 안내 카드(점선 테두리). 4-state 패턴의 'empty' 담당.
export function EmptyState({
  title,
  description,
  icon,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div
      className={`flex flex-col items-center rounded-2xl border border-dashed border-border-strong bg-surface-raised p-10 text-center ${className}`}
    >
      {icon ? (
        <div className="mb-3 text-fg-subtle" aria-hidden>
          {icon}
        </div>
      ) : null}
      <p className="text-body font-medium text-fg-strong">{title}</p>
      {description ? (
        <p className="mt-1 text-caption text-fg-muted">{description}</p>
      ) : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}
