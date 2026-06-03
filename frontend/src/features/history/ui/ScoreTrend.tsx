import type { UserStats } from '../api/historyApi'

// 최근 종합 점수 추이를 라이브러리 없이 inline 막대로. recent 는 최신순이라 뒤집어 시간순으로.
export function ScoreTrend({ stats }: { stats: UserStats }) {
  const points = [...(stats.recent ?? [])]
    .reverse()
    .filter((r) => typeof r.overall === 'number')

  if (points.length === 0) {
    return (
      <section className="flex flex-col gap-2 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
        <span className="text-caption text-fg-muted">점수 추이</span>
        <p className="text-body text-fg-muted">아직 채점된 면접이 없어요.</p>
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
      <span className="text-caption text-fg-muted">종합 점수 추이 (최근 {points.length}회)</span>
      <div className="flex h-32 items-end gap-2">
        {points.map((r) => {
          const score = Math.max(0, Math.min(100, r.overall as number))
          return (
            <div key={r.sessionId} className="flex flex-1 flex-col items-center gap-1">
              <div className="flex w-full flex-1 items-end">
                <div
                  className="w-full rounded-t bg-primary"
                  style={{ height: `${Math.max(score, 2)}%` }}
                  title={`${Math.round(score)}점`}
                />
              </div>
              <span className="text-caption text-fg-muted">{Math.round(score)}</span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
