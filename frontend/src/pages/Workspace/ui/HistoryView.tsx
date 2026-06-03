import {
  ScoreTrend,
  SessionHistoryList,
  StatsSummary,
  useUserStats,
} from '@/features/history'

export function HistoryView() {
  const { data: stats } = useUserStats()

  return (
    <div className="space-y-8">
      {stats && (
        <div className="grid gap-4 md:grid-cols-2">
          <StatsSummary stats={stats} />
          <ScoreTrend stats={stats} />
        </div>
      )}

      <section className="flex flex-col gap-3">
        <h2 className="text-h6 text-fg">지난 면접</h2>
        <SessionHistoryList />
      </section>
    </div>
  )
}
