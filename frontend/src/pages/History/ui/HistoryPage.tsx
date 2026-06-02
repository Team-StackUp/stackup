import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import {
  ScoreTrend,
  SessionHistoryList,
  StatsSummary,
  useUserStats,
} from '@/features/history'

export default function HistoryPage() {
  const { data: stats } = useUserStats()

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col gap-8 px-6 py-12 lg:px-12">
        <h1 className="text-h4 text-fg">면접 히스토리</h1>

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
      </main>
      <SiteFooter />
    </div>
  )
}
