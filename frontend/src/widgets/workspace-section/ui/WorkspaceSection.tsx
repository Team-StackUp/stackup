import type { ReactNode } from 'react'

//단순 위젯 수준은 model분리를 추후에 분리를 고려합니다.
type Props = {
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
}

export function WorkspaceSection({
  title,
  description,
  action,
  children,
}: Props) {
  return (
    <section>
      <header className="flex items-end justify-between gap-4 mb-4">
        <div>
          <h3 className="font-heading font-bold text-h5 text-fg-strong">
            {title}
          </h3>
          {description ? (
            <p className="text-body text-fg-muted mt-1">{description}</p>
          ) : null}
        </div>
        {action}
      </header>
      {children}
    </section>
  )
}
