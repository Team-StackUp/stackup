import { Code, Section } from '../primitives'

const RADII = [
  { token: 'sm', value: '4px', cls: 'rounded-sm' },
  { token: 'md', value: '8px', cls: 'rounded-md' },
  { token: 'lg', value: '12px', cls: 'rounded-lg' },
  { token: 'xl', value: '16px', cls: 'rounded-xl' },
  { token: '2xl', value: '24px', cls: 'rounded-2xl' },
  { token: 'pill', value: '9999px', cls: 'rounded-pill' },
]

export function RadiusSection() {
  return (
    <Section
      id="radius"
      label="03 RADIUS"
      title="라디우스"
      description="6 단계 — 뱃지부터 hero · pill 까지."
    >
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-5">
        {RADII.map((r) => (
          <div key={r.token} className="text-center">
            <div
              className={`h-20 bg-sage-200 border border-sage-300 mb-3 ${r.cls}`}
            />
            <div className="text-button font-semibold mb-1">
              <Code>{r.cls}</Code>
            </div>
            <div className="text-caption text-fg-muted font-mono">{r.value}</div>
          </div>
        ))}
      </div>
    </Section>
  )
}
