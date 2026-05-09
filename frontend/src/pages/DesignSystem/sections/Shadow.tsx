import { Code, Section } from '../primitives'

const SHADOWS = [
  { cls: 'shadow-sm', label: 'shadow-sm', desc: '카드 기본' },
  { cls: 'shadow-md', label: 'shadow-md', desc: '드롭다운' },
  { cls: 'shadow-lg', label: 'shadow-lg', desc: '모달' },
  { cls: 'shadow-focus-ring', label: 'shadow-focus-ring', desc: 'Focus ring' },
]

export function ShadowSection() {
  return (
    <Section
      id="shadow"
      label="04 SHADOW"
      title="섀도우 / Elevation"
      description="그림자 색에 sage tint (rgba 31,39,27) 사용. focus-ring 은 sage-500 기반 glow."
    >
      <div className="grid grid-cols-2 md:grid-cols-4 gap-6 bg-surface p-6 rounded-lg border border-border">
        {SHADOWS.map((s) => (
          <div
            key={s.label}
            className="flex flex-col items-center gap-3 text-center"
          >
            <div className={`w-28 h-16 bg-white rounded-md ${s.cls}`} />
            <div>
              <Code>{s.label}</Code>
              <div className="text-caption text-fg-muted mt-1">{s.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </Section>
  )
}
