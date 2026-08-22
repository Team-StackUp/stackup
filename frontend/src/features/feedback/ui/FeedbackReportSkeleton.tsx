import { useEffect, useState } from 'react'

function Pulse({ className }: { className: string }) {
  return <div aria-hidden className={`animate-pulse rounded-lg bg-surface ${className}`} />
}

// 피드백 생성 대기 화면 — 리포트가 올 자리의 형태를 미리 보여주고 경과 시간을 알린다.
// 정적 텍스트만 있으면 "멈췄나?" 로 읽힌다 (A4). 완료는 FEEDBACK_READY SSE 로 즉시 반영된다.
export function FeedbackReportSkeleton() {
  const [elapsedSec, setElapsedSec] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setElapsedSec((s) => s + 1), 1_000)
    return () => clearInterval(id)
  }, [])

  return (
    <div className="flex flex-col gap-8" role="status" aria-label="피드백 생성 중">
      <div className="flex flex-col items-center gap-2 py-4 text-center">
        <p className="text-body font-medium text-fg">피드백을 생성하는 중입니다…</p>
        <p className="text-caption text-fg-muted">
          답변을 종합 분석하고 있어요. 보통 1분 내외, 길면 2분 이상 걸릴 수 있습니다.
          완성되면 자동으로 표시됩니다. <span className="font-mono">({elapsedSec}s 경과)</span>
        </p>
      </div>

      {/* 종합 점수 + 축별 점수 바 자리 */}
      <div className="rounded-2xl border border-border bg-surface-raised/60 p-6">
        <Pulse className="mb-6 h-8 w-40" />
        <div className="flex flex-col gap-4">
          <Pulse className="h-4 w-full" />
          <Pulse className="h-4 w-5/6" />
          <Pulse className="h-4 w-4/6" />
        </div>
      </div>

      {/* 강점/개선 본문 자리 */}
      <div className="rounded-2xl border border-border bg-surface-raised/60 p-6">
        <Pulse className="mb-4 h-6 w-28" />
        <div className="flex flex-col gap-3">
          <Pulse className="h-3.5 w-full" />
          <Pulse className="h-3.5 w-full" />
          <Pulse className="h-3.5 w-3/4" />
        </div>
      </div>
    </div>
  )
}
