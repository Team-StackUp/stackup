import { Section } from '../primitives'

const ROWS: [string, string][] = [
  ['Color 출처', 'SEED Design 토큰 참조 (라이트/다크 자동 전환)'],
  ['Semantic', 'fg / bg / surface / border / primary — SEED 시맨틱 매핑'],
  ['Brand', 'SEED blue 팔레트 · 배경 primary / 텍스트 primary-fg 분리'],
  ['Status', 'success · warning · danger · info → SEED positive/warning/critical/informative'],
  ['고정 톤 스케일', 'sage-50 … sage-950 (11단계, mode 무관 — 다크 패널 전용)'],
  ['Domain', '직군 4 + 면접 유형 4 (muted jewel tone, 자체 값)'],
  ['Typography', 'Pretendard · Bricolage Grotesque · Geist Mono · 12단계 텍스트 스케일'],
  ['Radius', 'sm → 2xl + pill (SEED r1~r6)'],
  ['Shadow', 'sm · md · lg (SEED s1~s3) + focus-ring'],
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
      description="컬러·radius·shadow 는 당근 SEED Design 토큰을 참조하고, 우리 alias 는 frontend/src/app/styles/tokens.css 의 @theme 블록을 단일 출처로 한다."
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
