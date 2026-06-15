import type { UserStats } from '../api/historyApi'

const W = 320
const H = 140
const PAD = { l: 26, r: 10, t: 18, b: 10 }
const IW = W - PAD.l - PAD.r
const IH = H - PAD.t - PAD.b
const GRID = [0, 50, 100]

const clamp = (v: number) => Math.max(0, Math.min(100, v))

// 종합 점수 추이를 라이브러리 없이 SVG 추세선(라인+영역)으로. recent 는 최신순이라 뒤집어 시간순으로.
export function ScoreTrend({ stats }: { stats: UserStats }) {
  const points = [...(stats.recent ?? [])]
    .reverse()
    .filter((r) => typeof r.overall === 'number')
    .map((r) => ({ sessionId: r.sessionId, score: clamp(r.overall as number) }))

  if (points.length === 0) {
    return (
      <section className="flex flex-col gap-2 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
        <span className="text-caption text-fg-muted">점수 추이</span>
        <p className="text-body text-fg-muted">아직 채점된 면접이 없어요.</p>
      </section>
    )
  }

  const n = points.length
  const sx = (i: number) => (n <= 1 ? PAD.l + IW / 2 : PAD.l + (IW * i) / (n - 1))
  const sy = (s: number) => PAD.t + IH * (1 - s / 100)
  const data = points.map((p, i) => ({ ...p, x: sx(i), y: sy(p.score) }))
  const linePts = data.map((d) => `${d.x.toFixed(1)},${d.y.toFixed(1)}`).join(' ')
  const areaPts = `${data[0].x.toFixed(1)},${PAD.t + IH} ${linePts} ${data[n - 1].x.toFixed(1)},${PAD.t + IH}`

  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
      <span className="text-caption text-fg-muted">종합 점수 추이 (최근 {n}회)</span>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="h-36 w-full"
        preserveAspectRatio="none"
        role="img"
        aria-label={`종합 점수 추이, 최근 ${n}회: ${data.map((d) => `${d.score}점`).join(', ')}`}
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

        {/* 영역 + 추세선 (점 2개 이상일 때) */}
        {n >= 2 && (
          <>
            <polygon points={areaPts} style={{ fill: 'var(--color-primary)' }} fillOpacity={0.12} />
            <polyline
              points={linePts}
              fill="none"
              style={{ stroke: 'var(--color-primary)' }}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          </>
        )}

        {/* 데이터 포인트 + 값 라벨 */}
        {data.map((d) => (
          <g key={d.sessionId}>
            <circle cx={d.x} cy={d.y} r={3} style={{ fill: 'var(--color-primary)' }} />
            <text
              x={d.x}
              y={d.y - 7}
              textAnchor="middle"
              style={{ fill: 'var(--color-fg)' }}
              fontSize={10}
              fontWeight={600}
            >
              {d.score}
            </text>
          </g>
        ))}
      </svg>
    </section>
  )
}
