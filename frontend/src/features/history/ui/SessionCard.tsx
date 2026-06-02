import { Link } from 'react-router-dom'
import { StatusBadge, type StatusTone } from '@/shared/ui'
import { formatDate } from '@/shared/utils'
import type { Session } from '../api/historyApi'

const STATUS: Record<string, { label: string; tone: StatusTone }> = {
  READY: { label: '준비', tone: 'neutral' },
  IN_PROGRESS: { label: '진행 중', tone: 'info' },
  COMPLETED: { label: '완료', tone: 'success' },
  INTERRUPTED: { label: '중단됨', tone: 'warning' },
  CANCELLED: { label: '취소됨', tone: 'neutral' },
}

const MODE: Record<string, string> = {
  TECHNICAL: '기술',
  PERSONALITY: '인성',
  INTEGRATED: '통합',
}

export function SessionCard({ session }: { session: Session }) {
  const status = session.status ? STATUS[session.status] : undefined
  const completed = session.status === 'COMPLETED'

  const body = (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-border bg-surface-raised px-5 py-4 transition-colors hover:bg-surface">
      <div className="flex flex-col gap-1">
        <span className="text-body text-fg">{session.title || `면접 #${session.id}`}</span>
        <span className="text-caption text-fg-muted">
          {formatDate(session.createdAt)} · {MODE[session.mode ?? ''] ?? session.mode} ·{' '}
          {session.jobCategory} · 질문 {session.totalQuestionCount ?? 0}개
        </span>
      </div>
      <div className="flex items-center gap-3">
        {status && <StatusBadge tone={status.tone}>{status.label}</StatusBadge>}
        {completed && <span className="text-caption text-fg-muted">리포트 →</span>}
      </div>
    </div>
  )

  return completed ? (
    <Link to={`/sessions/${session.id}/feedback`}>{body}</Link>
  ) : (
    body
  )
}
