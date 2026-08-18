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
  JOB_TAILORED: '직무 맞춤',
}

const JOB: Record<string, string> = {
  FRONTEND: '프론트엔드',
  BACKEND: '백엔드',
  INFRA: '인프라',
  DBA: 'DBA',
}

/**
 * 상태별로 눌렀을 때 갈 곳. 예전에는 COMPLETED 만 링크였고 나머지는 아예 눌리지 않아,
 * 중단된 면접의 문답을 다시 볼 방법도 · 진행 중이던 면접으로 돌아갈 방법도 없었다.
 * (CANCELLED 는 시작 전 취소라 볼 것이 없어 그대로 둔다.)
 */
const LINK: Record<string, { to: (id: number) => string; cta: string }> = {
  COMPLETED: { to: (id) => `/sessions/${id}/feedback`, cta: '리포트 →' },
  INTERRUPTED: { to: (id) => `/sessions/${id}`, cta: '기록 보기 →' },
  IN_PROGRESS: { to: (id) => `/sessions/${id}`, cta: '이어서 →' },
  READY: { to: (id) => `/sessions/${id}`, cta: '시작하기 →' },
}

export function SessionCard({ session }: { session: Session }) {
  const status = session.status ? STATUS[session.status] : undefined
  const link = session.status ? LINK[session.status] : undefined
  const jobs = session.jobCategories?.length
    ? session.jobCategories
    : session.jobCategory
      ? [session.jobCategory]
      : []
  const jobLabel = jobs.map((j) => JOB[j] ?? j).join('·')

  const body = (
    <div className="flex items-center justify-between gap-4 rounded-xl border border-border bg-surface-raised px-5 py-4 transition-colors duration-fast hover:border-border-strong">
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-body font-semibold tracking-[-0.02em] text-fg">
          {session.title || `면접 #${session.id}`}
        </span>
        <span className="text-caption text-fg-subtle">
          {formatDate(session.createdAt)} · {MODE[session.mode ?? ''] ?? session.mode} ·{' '}
          {jobLabel} · 질문 {session.totalQuestionCount ?? 0}개
        </span>
      </div>
      <div className="flex items-center gap-3">
        {status && <StatusBadge tone={status.tone}>{status.label}</StatusBadge>}
        {link && (
          <span className="shrink-0 text-caption font-medium text-primary-fg">
            {link.cta}
          </span>
        )}
      </div>
    </div>
  )

  return link && session.id != null ? (
    <Link to={link.to(session.id)}>{body}</Link>
  ) : (
    body
  )
}
