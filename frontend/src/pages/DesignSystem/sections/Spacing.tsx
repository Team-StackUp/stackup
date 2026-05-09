import { Section } from '../primitives'

const SCALE = [1, 2, 3, 4, 5, 6, 8, 10, 12, 16]

export function SpacingSection() {
  return (
    <Section
      id="spacing"
      label="05 SPACING"
      title="간격"
      description="4px 기본 grid. Tailwind v4 default scale 사용 (n × 4px)."
    >
      <div className="space-y-2.5">
        {SCALE.map((n) => (
          <div key={n} className="flex items-center gap-4">
            <div className="w-16 shrink-0 text-button font-mono text-fg-muted">
              space-{n}
            </div>
            <div
              className="bg-primary h-3 rounded-sm"
              style={{ width: `${n * 4}px` }}
            />
            <div className="text-caption text-fg-muted font-mono">
              {n * 4}px
            </div>
          </div>
        ))}
      </div>
    </Section>
  )
}
