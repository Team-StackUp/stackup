// 0~100 점수를 라벨 + 막대로 표시. 점수 없으면(null) '미산정'.
export function ScoreBar({ label, score }: { label: string; score?: number | null }) {
  const has = typeof score === 'number'
  const pct = has ? Math.max(0, Math.min(100, score as number)) : 0
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-baseline justify-between">
        <span className="text-body text-fg">{label}</span>
        <span className="text-caption text-fg-muted">
          {has ? `${Math.round(pct)}점` : '미산정'}
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-pill bg-surface">
        {has && (
          <div
            className="h-full rounded-pill bg-primary"
            style={{ width: `${pct}%` }}
          />
        )}
      </div>
    </div>
  )
}
