import { Code, Section, Sub } from '../primitives'

const SAMPLE = 'STACK-UP · 빠른 면접, 깊은 피드백'

const FONT_FAMILIES = [
  {
    token: '--font-heading / display',
    utility: 'font-heading',
    name: 'Fira Sans Extra Condensed',
    usage: 'H1 · H2 · H3 (Uppercase)',
    cls: 'font-heading text-h4 uppercase font-bold',
  },
  {
    token: '--font-subheading',
    utility: 'font-subheading',
    name: 'Geist',
    usage: 'H4 · H5 · H6 · Caption',
    cls: 'font-subheading text-h4 font-bold',
  },
  {
    token: '--font-sans / body',
    utility: 'font-sans',
    name: 'Inter',
    usage: '본문 · 버튼',
    cls: 'font-sans text-h4',
  },
  {
    token: '--font-mono',
    utility: 'font-mono',
    name: 'Geist Mono',
    usage: '코드 · 숫자',
    cls: 'font-mono text-h5',
  },
]

const TEXT_SCALE = [
  { token: 'text-display', cls: 'font-display text-display-mobile lg:text-display uppercase', size: '100 / 48', weight: 700, usage: '랜딩 타이틀' },
  { token: 'text-h2', cls: 'font-heading text-h2-mobile lg:text-h2 uppercase', size: '56 / 42', weight: 700, usage: '섹션 제목' },
  { token: 'text-h3', cls: 'font-heading text-h3 uppercase', size: '38', weight: 700, usage: '카드 제목' },
  { token: 'text-h4', cls: 'font-subheading text-h4', size: '32', weight: 700, usage: '서브헤딩 L' },
  { token: 'text-h5', cls: 'font-subheading text-h5', size: '24', weight: 700, usage: '서브헤딩 M' },
  { token: 'text-h6', cls: 'font-subheading text-h6', size: '20', weight: 700, usage: '서브헤딩 S' },
  { token: 'text-rich', cls: 'font-subheading text-rich', size: '20', weight: 500, usage: '큰 본문 / 강조' },
  { token: 'text-body', cls: 'font-sans text-body', size: '16', weight: 500, usage: '기본 본문' },
  { token: 'text-button', cls: 'font-sans text-button', size: '14', weight: 600, usage: '버튼 · 작은 본문' },
  { token: 'text-caption', cls: 'font-subheading text-caption', size: '12', weight: 400, usage: '캡션 · 메타' },
]

export function TypographySection() {
  return (
    <Section
      id="typography"
      label="02 TYPOGRAPHY"
      title="타이포그래피"
      description="Fira Sans Extra Condensed (Heading) · Geist (Subheading) · Inter (Body) 3-Tier."
    >
      <Sub title="Font Families">
        <div className="space-y-3">
          {FONT_FAMILIES.map((f) => (
            <div
              key={f.utility}
              className="flex items-center gap-4 bg-surface-raised border border-border rounded-md px-5 py-4 hover:border-border-strong transition-colors duration-fast"
            >
              <div className={`${f.cls} text-fg-strong flex-1 min-w-0 truncate`}>
                {f.name}
              </div>
              <div className="shrink-0 text-right space-y-1">
                <div>
                  <Code>{f.utility}</Code>
                </div>
                <div className="text-caption text-fg-muted">{f.usage}</div>
              </div>
            </div>
          ))}
        </div>
      </Sub>

      <Sub title="Text Scale">
        <div className="space-y-5">
          {TEXT_SCALE.map((t) => (
            <div
              key={t.token}
              className="border-b border-border last:border-0 pb-5 last:pb-0"
            >
              <div className={`${t.cls} text-fg-strong`}>{SAMPLE}</div>
              <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
                <Code>{t.token}</Code>
                <span className="text-caption text-fg-muted font-mono">
                  {t.size}px
                </span>
                <span className="text-caption text-fg-muted font-mono">
                  w{t.weight}
                </span>
                <span className="text-caption text-fg-muted">{t.usage}</span>
              </div>
            </div>
          ))}
        </div>
      </Sub>
    </Section>
  )
}
