import { Section } from '../primitives'

const ROWS: [string, string][] = [
  ['Sage Scale', 'sage-50 … sage-950 (11단계)'],
  ['Semantic', '14개 별칭 — fg / bg / surface / border / primary'],
  ['Status', 'success · warning · danger · info × 3 shades'],
  ['Domain', '직군 4 + 면접 유형 4 (muted jewel tone)'],
  ['Typography', 'Fira / Geist / Inter 3-Tier · 10단계 텍스트 스케일'],
  ['Radius', 'sm → 2xl + pill 6단계'],
  ['Shadow', 'sm · md · lg · focus-ring (sage-tint)'],
  ['Spacing', '4px grid — Tailwind 기본 scale'],
  ['Motion', '120 / 200 / 320ms + 3 easing'],
  ['Z-Index', '9단계 (0 → 1600)'],
  ['Container', 'readable 65ch · content 1280px · app 1440px'],
]

export function OverviewSection() {
  return (
    <Section
      id="overview"
      label="00 OVERVIEW"
      title="토큰 현황"
      description="9 개 카테고리의 디자인 토큰. 모든 정의는 frontend/src/app/styles/tokens.css 의 @theme 블록을 단일 출처로 한다."
    >
      <dl className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
        {ROWS.map(([k, v]) => (
          <div
            key={k}
            className="flex justify-between gap-4 bg-surface-raised border border-border rounded-md px-4 py-3 hover:border-border-strong transition-colors duration-fast"
          >
            <dt className="text-button text-fg-muted shrink-0">{k}</dt>
            <dd className="text-button text-fg-strong text-right">{v}</dd>
          </div>
        ))}
      </dl>
    </Section>
  )
}
