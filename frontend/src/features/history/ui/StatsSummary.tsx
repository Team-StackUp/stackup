import { ScoreBar } from '@/shared/ui/ScoreBar'
import type { UserStats } from '../api/historyApi'

export function StatsSummary({ stats }: { stats: UserStats }) {
  const a = stats.averages
  return (
    <section className="flex flex-col gap-4 rounded-lg border border-border bg-surface-raised p-5">
      <div className="flex gap-8">
        <Stat label="총 면접" value={stats.totalSessionCount ?? 0} />
        <Stat label="완료" value={stats.completedSessionCount ?? 0} />
      </div>
      {a && (
        <div className="flex flex-col gap-3">
          <span className="text-caption text-fg-muted">평균 점수</span>
          <ScoreBar label="종합" score={a.overall} />
          <ScoreBar label="기술 정확도" score={a.technical} />
          <ScoreBar label="논리력" score={a.logic} />
          <ScoreBar label="전달력" score={a.communication} />
        </div>
      )}
    </section>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col">
      <span className="text-h4 text-fg">{value}</span>
      <span className="text-caption text-fg-muted">{label}</span>
    </div>
  )
}
