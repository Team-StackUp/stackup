import { Link } from 'react-router-dom'
import {
  ScoreTrend,
  StatsSummary,
  useUserStats,
} from '@/features/history'
import { Button } from '@/shared/ui/Button'

export function HomeView() {
  const { data: stats } = useUserStats()
  const hasSessions = (stats?.totalSessionCount ?? 0) > 0

  return (
    <div className="space-y-8">
      <section className="overflow-hidden rounded-2xl border border-border bg-sage-600 p-8 text-white">
        <p className="text-caption uppercase tracking-[0.08em] text-white/60">
          맞춤 모의 면접
        </p>
        <h2 className="mt-2 font-heading text-h4 font-bold">
          이력서·레포 기반 맞춤 면접을 시작하세요
        </h2>
        <p className="mt-2 max-w-xl text-body text-white/70">
          면접 모드와 직군을 고르면 AI가 질문을 생성하고, 실시간으로 답변을
          주고받습니다.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link to="/sessions/new">
            <Button size="lg" variant="secondary">
              새 면접 시작
            </Button>
          </Link>
          <Link to="/workspace/resumes">
            <Button
              size="lg"
              variant="ghost"
              className="text-white hover:bg-white/10"
            >
              자료 준비하기
            </Button>
          </Link>
        </div>
      </section>

      <div className="grid gap-4 md:grid-cols-3">
        <QuickLink
          to="/workspace/resumes"
          title="이력서"
          description="이력서를 업로드하고 분석 결과를 확인하세요."
        />
        <QuickLink
          to="/workspace/repos"
          title="레포지토리"
          description="GitHub 레포를 등록하고 분석 결과를 확인하세요."
        />
        <QuickLink
          to="/workspace/history"
          title="면접 히스토리"
          description="지난 면접 기록과 점수 추이를 확인하세요."
        />
      </div>

      {hasSessions && stats && (
        <section className="space-y-3">
          <h2 className="text-h6 text-fg">한눈에 보기</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <StatsSummary stats={stats} />
            <ScoreTrend stats={stats} />
          </div>
        </section>
      )}
    </div>
  )
}

function QuickLink({
  to,
  title,
  description,
}: {
  to: string
  title: string
  description: string
}) {
  return (
    <Link
      to={to}
      className="group flex items-center justify-between gap-4 rounded-xl border border-border bg-surface-raised p-5 transition-colors duration-fast hover:border-border-strong hover:bg-surface"
    >
      <div>
        <h3 className="font-heading text-h6 font-bold text-fg-strong">
          {title}
        </h3>
        <p className="mt-1 text-caption text-fg-muted">{description}</p>
      </div>
      <span
        aria-hidden
        className="text-fg-muted transition-transform duration-fast group-hover:translate-x-0.5"
      >
        →
      </span>
    </Link>
  )
}
