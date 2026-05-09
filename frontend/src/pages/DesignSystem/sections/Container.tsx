import { Code, Section, Sub } from '../primitives'

const CONTAINERS = [
  {
    token: '--container-readable',
    utility: 'max-w-readable',
    value: '65ch',
    pct: 45,
    usage: '본문 · 프로즈 카드',
  },
  {
    token: '--container-content',
    utility: 'max-w-content',
    value: '1280px',
    pct: 89,
    usage: 'TopNav 안 메인 영역',
  },
  {
    token: '--container-app',
    utility: 'max-w-app',
    value: '1440px',
    pct: 100,
    usage: '앱 전체 최대 폭',
  },
]

const BREAKPOINTS = [
  { label: 'sm', value: '640px' },
  { label: 'md', value: '768px' },
  { label: 'lg', value: '1024px' },
  { label: 'xl', value: '1280px' },
  { label: '2xl', value: '1536px' },
]

export function ContainerSection() {
  return (
    <Section
      id="container"
      label="08 CONTAINER"
      title="컨테이너"
      description="max-w-* 유틸리티로 사용. Tailwind v4 --container-* 네임스페이스."
    >
      <Sub title="Container max-width">
        <div className="space-y-4">
          {CONTAINERS.map((c) => (
            <div
              key={c.token}
              className="bg-surface-raised border border-border rounded-lg p-4 hover:border-border-strong transition-colors duration-fast"
            >
              <div className="flex items-center justify-between gap-3 mb-3">
                <div className="flex items-center gap-2">
                  <Code>{c.utility}</Code>
                  <span className="font-mono text-button text-fg-strong">
                    {c.value}
                  </span>
                </div>
                <span className="text-caption text-fg-muted">{c.usage}</span>
              </div>
              <div className="bg-surface rounded-sm h-3 w-full overflow-hidden">
                <div
                  className="h-full bg-primary rounded-sm"
                  style={{ width: `${c.pct}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      </Sub>

      <Sub title="Breakpoints (Tailwind v4 Default)">
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          {BREAKPOINTS.map((bp) => (
            <div
              key={bp.label}
              className="bg-surface-raised border border-border rounded-md px-3 py-3 text-center hover:border-border-strong transition-colors duration-fast"
            >
              <div className="font-subheading text-h6 font-bold text-fg-strong">
                {bp.label}
              </div>
              <div className="text-caption text-fg-muted font-mono mt-1">
                {bp.value}
              </div>
            </div>
          ))}
        </div>
      </Sub>
    </Section>
  )
}
