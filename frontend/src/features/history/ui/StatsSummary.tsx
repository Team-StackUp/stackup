import { Eyebrow, Panel } from '@/shared/ui'
import { ScoreBar } from '@/shared/ui/ScoreBar'
import type { UserStats } from '../api/historyApi'

export function StatsSummary({ stats }: { stats: UserStats }) {
  const a = stats.averages
  return (
    <Panel as="section" className="flex flex-col gap-5">
      {/* 숫자 조판은 랜딩 히어로의 지표 블록과 같은 규칙 — 큰 수치 + 작은 캡션, 헤어라인 구분 */}
      <dl className="flex divide-x divide-border">
        <Stat label="총 면접" value={stats.totalSessionCount ?? 0} />
        <Stat label="완료" value={stats.completedSessionCount ?? 0} />
      </dl>
      {a && (
        <div className="flex flex-col gap-3 border-t border-border pt-4">
          <Eyebrow>평균 점수</Eyebrow>
          <ScoreBar label="종합" score={a.overall} />
          <ScoreBar label="기술 정확도" score={a.technical} />
          <ScoreBar label="논리력" score={a.logic} />
          <ScoreBar label="전달력" score={a.communication} />
        </div>
      )}
    </Panel>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex-1 pr-4 first:pl-0 [&:not(:first-child)]:pl-5">
      <dd
        className="font-sans font-bold leading-none text-fg"
        style={{ fontSize: '26px', letterSpacing: '-0.04em' }}
      >
        {value}
      </dd>
      <dt className="mt-1.5 text-caption text-fg-subtle">{label}</dt>
    </div>
  )
}
