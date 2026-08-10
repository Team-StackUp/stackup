import {
  ScoreTrend,
  SessionHistoryList,
  StatsSummary,
  useUserStats,
} from '@/features/history'
import { QueryError } from '@/shared/ui'

export function HistoryView() {
  const { data: stats, isError, refetch } = useUserStats()

  return (
    <div className="space-y-8">
      {/* 실패를 조용히 감추면 "면접을 여러 번 했는데 통계가 사라졌다"로 읽힌다. */}
      {isError && <QueryError message="점수 통계를 불러오지 못했습니다." onRetry={() => refetch()} className="py-4" />}
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
