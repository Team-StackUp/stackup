import type { UserStats } from '../api/historyApi'

const W = 480
const H = 220
const PAD = { l: 30, r: 14, t: 18, b: 16 }
const IW = W - PAD.l - PAD.r
const IH = H - PAD.t - PAD.b
const GRID = [0, 50, 100]

const clamp = (v: number) => Math.max(0, Math.min(100, v))

type MetricKey = 'overall' | 'technical' | 'logic' | 'communication'
const METRICS: { key: MetricKey; label: string; color: string }[] = [
  { key: 'overall', label: '종합', color: 'var(--color-primary)' },
  { key: 'technical', label: '기술', color: 'var(--color-info)' },
  { key: 'logic', label: '논리', color: 'var(--color-success)' },
  { key: 'communication', label: '전달력', color: 'var(--color-warning)' },
]

// 지표별(종합·기술·논리·전달력) 점수 추이를 라이브러리 없이 SVG 멀티 라인으로.
// recent 는 최신순이라 뒤집어 시간순으로, 종합이 채점된 세션을 x축 스파인으로 쓴다.
export function ScoreTrend({ stats }: { stats: UserStats }) {
  const sessions = [...(stats.recent ?? [])]
    .reverse()
    .filter((r) => typeof r.overall === 'number')
  const n = sessions.length

  if (n === 0) {
    return (
      <section className="flex flex-col gap-2 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
        <span className="text-caption text-fg-muted">점수 추이</span>
        <p className="text-body text-fg-muted">아직 채점된 면접이 없어요.</p>
      </section>
    )
  }

  const sx = (i: number) => (n <= 1 ? PAD.l + IW / 2 : PAD.l + (IW * i) / (n - 1))
  const sy = (s: number) => PAD.t + IH * (1 - s / 100)

  const series = METRICS.map((m) => {
    const pts = sessions
      .map((s, i) =>
        typeof s[m.key] === 'number'
          ? { x: sx(i), y: sy(clamp(s[m.key] as number)) }
          : null,
      )
      .filter((p): p is { x: number; y: number } => p !== null)
    // 최신 점수 + 지난번(바로 직전 세션) 대비 델타. sparse 지표에서 비인접 세션끼리 비교하지 않는다.
    const seq = sessions.map((s) => (typeof s[m.key] === 'number' ? (s[m.key] as number) : null))
    let latestIdx = -1
    for (let i = seq.length - 1; i >= 0; i--) {
      if (seq[i] !== null) {
        latestIdx = i
        break
      }
    }
    const latest = latestIdx >= 0 ? Math.round(seq[latestIdx] as number) : null
    const prev = latestIdx > 0 ? seq[latestIdx - 1] : null
    const delta =
      latest !== null && prev !== null ? Math.round((seq[latestIdx] as number) - prev) : null
    return { ...m, pts, latest, delta }
  })

  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
      <span className="text-caption text-fg-muted">지표별 점수 추이 (최근 {n}회)</span>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="aspect-[24/11] w-full"
        role="img"
        aria-label={`지표별 점수 추이, 최근 ${n}회. ${series
          .map((s) => `${s.label} ${s.latest ?? '미산정'}`)
          .join(', ')}`}
      >
        {/* y축 가이드라인 + 눈금(0/50/100) */}
        {GRID.map((g) => {
          const y = sy(g)
          return (
            <g key={g}>
              <line
                x1={PAD.l}
                y1={y}
                x2={W - PAD.r}
                y2={y}
                style={{ stroke: 'var(--color-border)' }}
                strokeWidth={1}
              />
              <text
                x={PAD.l - 5}
                y={y + 3}
                textAnchor="end"
                style={{ fill: 'var(--color-fg-muted)' }}
                fontSize={9}
              >
                {g}
              </text>
            </g>
          )
        })}

        {/* 지표별 추세선 (점 2개 이상일 때) */}
        {series.map(
          (s) =>
            s.pts.length >= 2 && (
              <polyline
                key={s.key}
                points={s.pts.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')}
                fill="none"
                style={{ stroke: s.color }}
                strokeWidth={1.75}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            ),
        )}
        {/* 데이터 포인트 */}
        {series.map((s) =>
          s.pts.map((p, i) => (
            <circle
              key={`${s.key}-${i}`}
              cx={p.x}
              cy={p.y}
              r={2}
              style={{ fill: s.color }}
            />
          )),
        )}
      </svg>

      {/* 범례 — 지표별 최신 점수 + 지난번 대비 델타 */}
      <div className="flex flex-wrap gap-x-4 gap-y-1.5">
        {series.map((s) => (
          <div key={s.key} className="flex items-center gap-1.5 text-caption">
            <span
              aria-hidden
              className="inline-block h-2 w-2 rounded-full"
              style={{ backgroundColor: s.color }}
            />
            <span className="text-fg-muted">{s.label}</span>
            <span className="font-medium text-fg">{s.latest ?? '—'}</span>
            {s.delta != null && s.delta !== 0 && (
              <span className={s.delta > 0 ? 'text-success-700' : 'text-danger-700'}>
                {s.delta > 0 ? `▲${s.delta}` : `▼${Math.abs(s.delta)}`}
              </span>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}
