import { StatusBadge } from '@/shared/ui/StatusBadge'
import { Button } from '@/shared/ui/Button'
import { sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'
import type { ConnectionStatus } from '../../model/useLiveInterview'

const connTone: Record<ConnectionStatus, 'success' | 'warning' | 'danger'> = {
  open: 'success',
  connecting: 'warning',
  closed: 'danger',
}
const connLabel: Record<ConnectionStatus, string> = {
  open: '연결됨',
  connecting: '연결 중',
  closed: '재연결 중',
}

export function InterviewHeader({
  session,
  connection,
  onEnd,
}: {
  session: Session
  connection: ConnectionStatus
  onEnd: () => void
}) {
  const progress = sessionProgress(session)
  return (
    <header className="flex items-center justify-between gap-4 border-b border-border bg-surface-raised px-4 py-3">
      <div className="min-w-0">
        <h1 className="truncate text-h6 text-fg">{session.title ?? '모의 면접'}</h1>
        <p className="text-caption text-fg-muted">
          질문 {progress.current} / {progress.max}
        </p>
      </div>
      <div className="flex items-center gap-3">
        <StatusBadge tone={connTone[connection]}>{connLabel[connection]}</StatusBadge>
        <Button variant="danger" size="sm" onClick={onEnd}>
          면접 종료
        </Button>
      </div>
    </header>
  )
}
