import { useState, type ReactNode, type CSSProperties } from 'react'

type SectionId =
  | 'overview'
  | 'colors'
  | 'typography'
  | 'radius'
  | 'shadow'
  | 'spacing'
  | 'motion'

const NAV: { id: SectionId; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'colors', label: 'Colors' },
  { id: 'typography', label: 'Typography' },
  { id: 'radius', label: 'Radius' },
  { id: 'shadow', label: 'Shadow' },
  { id: 'spacing', label: 'Spacing' },
  { id: 'motion', label: 'Motion' },
]

const PRETENDARD: CSSProperties = {
  fontFamily: "'Pretendard Variable', 'Pretendard', system-ui, sans-serif",
}

const JETBRAINS: CSSProperties = {
  fontFamily: "'JetBrains Mono', ui-monospace, monospace",
}

// =========================================================
// Color Data
// =========================================================
type ColorToken = { token: string; hex: string; alias?: string }

const SAGE_SCALE: ColorToken[] = [
  { token: 'sage-50', hex: '#e8e7e1' },
  { token: 'sage-100', hex: '#d4cfcb' },
  { token: 'sage-200', hex: '#c9ccc8' },
  { token: 'sage-300', hex: '#b4bdaf' },
  { token: 'sage-400', hex: '#a0a89d' },
  { token: 'sage-500', hex: '#626e5c' },
  { token: 'sage-600', hex: '#3e4739' },
  { token: 'sage-700', hex: '#2b3625' },
  { token: 'sage-800', hex: '#1f271b' },
  { token: 'sage-900', hex: '#181e15' },
  { token: 'sage-950', hex: '#141a11' },
]

const NEW_BASE: ColorToken[] = [
  { token: 'background', hex: '#e9e8e7' },
  { token: 'white', hex: '#ffffff' },
  { token: 'black', hex: '#000000' },
]

const NEW_SEMANTIC: ColorToken[] = [
  { token: 'primary', hex: '#626e5c', alias: 'sage-500' },
  { token: 'primary-hover', hex: '#3e4739', alias: 'sage-600' },
  { token: 'primary-pressed', hex: '#2b3625', alias: 'sage-700' },
  { token: 'fg', hex: '#141a11', alias: 'sage-950' },
  { token: 'fg-strong', hex: '#1f271b', alias: 'sage-800' },
  { token: 'fg-muted', hex: '#a0a89d', alias: 'sage-400' },
  { token: 'surface', hex: '#e8e7e1', alias: 'sage-50' },
  { token: 'border', hex: '#d4cfcb', alias: 'sage-100' },
]

const OLD_BRAND: ColorToken[] = [
  { token: 'brand-primary', hex: '#2563EB' },
  { token: 'brand-primary-hover', hex: '#1D4ED8' },
  { token: 'brand-primary-pressed', hex: '#1E40AF' },
  { token: 'brand-secondary', hex: '#10B981' },
]

const OLD_SEMANTIC: ColorToken[] = [
  { token: 'success', hex: '#10B981' },
  { token: 'warning', hex: '#F59E0B' },
  { token: 'danger', hex: '#EF4444' },
  { token: 'info', hex: '#3B82F6' },
]

const OLD_NEUTRAL: ColorToken[] = [
  { token: 'bg', hex: '#FFFFFF' },
  { token: 'surface', hex: '#F9FAFB' },
  { token: 'border', hex: '#E5E7EB' },
  { token: 'text-primary', hex: '#111827' },
  { token: 'text-secondary', hex: '#6B7280' },
]

const OLD_DOMAIN: ColorToken[] = [
  { token: 'job-frontend', hex: '#22D3EE' },
  { token: 'job-backend', hex: '#A78BFA' },
  { token: 'job-infra', hex: '#FB7185' },
  { token: 'job-dba', hex: '#FACC15' },
  { token: 'type-personality', hex: '#34D399' },
  { token: 'type-technical', hex: '#60A5FA' },
  { token: 'type-live-coding', hex: '#F472B6' },
  { token: 'type-integrated', hex: '#A78BFA' },
]

// =========================================================
// Type Data
// =========================================================
type NewType = {
  token: string
  family: string
  size: string
  weight: number
  className: string
}
type OldType = {
  token: string
  family: string
  size: string
  weight: number
  style: CSSProperties
}

const NEW_TYPE: NewType[] = [
  {
    token: 'text-display',
    family: 'Fira Sans Extra Condensed',
    size: '100 / 1.0',
    weight: 700,
    className: 'font-display text-display-mobile lg:text-display uppercase',
  },
  {
    token: 'text-h2',
    family: 'Fira Sans Extra Condensed',
    size: '56 / 1.05',
    weight: 700,
    className: 'font-heading text-h2-mobile lg:text-h2 uppercase',
  },
  {
    token: 'text-h3',
    family: 'Fira Sans Extra Condensed',
    size: '38 / 1.1',
    weight: 700,
    className: 'font-heading text-h3 uppercase',
  },
  {
    token: 'text-h4',
    family: 'Geist',
    size: '32 / 1.2',
    weight: 700,
    className: 'font-subheading text-h4',
  },
  {
    token: 'text-h5',
    family: 'Geist',
    size: '24 / 1.3',
    weight: 700,
    className: 'font-subheading text-h5',
  },
  {
    token: 'text-h6',
    family: 'Geist',
    size: '20 / 1.4',
    weight: 700,
    className: 'font-subheading text-h6',
  },
  {
    token: 'text-rich',
    family: 'Geist',
    size: '20 / 1.5',
    weight: 500,
    className: 'font-subheading text-rich',
  },
  {
    token: 'text-body',
    family: 'Inter',
    size: '16 / 1.2',
    weight: 500,
    className: 'font-sans text-body',
  },
  {
    token: 'text-button',
    family: 'Inter',
    size: '14 / 1.2',
    weight: 600,
    className: 'font-sans text-button',
  },
  {
    token: 'text-caption',
    family: 'Geist',
    size: '12 / 1.4',
    weight: 400,
    className: 'font-subheading text-caption',
  },
]

const OLD_TYPE: OldType[] = [
  {
    token: '--type-display',
    family: 'Pretendard',
    size: '48 / 56',
    weight: 700,
    style: { ...PRETENDARD, fontSize: 48, lineHeight: '56px', fontWeight: 700 },
  },
  {
    token: '--type-h1',
    family: 'Pretendard',
    size: '32 / 40',
    weight: 700,
    style: { ...PRETENDARD, fontSize: 32, lineHeight: '40px', fontWeight: 700 },
  },
  {
    token: '--type-h2',
    family: 'Pretendard',
    size: '24 / 32',
    weight: 700,
    style: { ...PRETENDARD, fontSize: 24, lineHeight: '32px', fontWeight: 700 },
  },
  {
    token: '--type-h3',
    family: 'Pretendard',
    size: '20 / 28',
    weight: 600,
    style: { ...PRETENDARD, fontSize: 20, lineHeight: '28px', fontWeight: 600 },
  },
  {
    token: '--type-h4',
    family: 'Pretendard',
    size: '18 / 26',
    weight: 600,
    style: { ...PRETENDARD, fontSize: 18, lineHeight: '26px', fontWeight: 600 },
  },
  {
    token: '--type-body-lg',
    family: 'Pretendard',
    size: '16 / 24',
    weight: 400,
    style: { ...PRETENDARD, fontSize: 16, lineHeight: '24px', fontWeight: 400 },
  },
  {
    token: '--type-body-md',
    family: 'Pretendard',
    size: '14 / 20',
    weight: 400,
    style: { ...PRETENDARD, fontSize: 14, lineHeight: '20px', fontWeight: 400 },
  },
  {
    token: '--type-body-sm',
    family: 'Pretendard',
    size: '12 / 18',
    weight: 400,
    style: { ...PRETENDARD, fontSize: 12, lineHeight: '18px', fontWeight: 400 },
  },
  {
    token: '--type-code-md',
    family: 'JetBrains Mono',
    size: '14 / 20',
    weight: 400,
    style: { ...JETBRAINS, fontSize: 14, lineHeight: '20px', fontWeight: 400 },
  },
]

// =========================================================
// Helpers
// =========================================================
function luminance(hex: string): number {
  const h = hex.replace('#', '')
  const r = parseInt(h.substring(0, 2), 16) / 255
  const g = parseInt(h.substring(2, 4), 16) / 255
  const b = parseInt(h.substring(4, 6), 16) / 255
  const lin = (v: number) =>
    v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
}

function readableFg(hex: string): string {
  return luminance(hex) > 0.55 ? '#1f271b' : '#ffffff'
}

// =========================================================
// Layout Components
// =========================================================
function Sidebar({
  active,
  onSelect,
}: {
  active: SectionId
  onSelect: (id: SectionId) => void
}) {
  return (
    <aside className="hidden lg:block w-60 shrink-0 sticky top-0 h-screen overflow-y-auto border-r border-border bg-surface px-5 py-6">
      <div className="mb-8">
        <div className="font-heading text-h5 font-bold uppercase tracking-tight leading-none text-fg-strong">
          StackUp
        </div>
        <div className="mt-1 text-caption text-fg-muted">
          Design System Demo
        </div>
      </div>
      <nav className="space-y-0.5">
        {NAV.map((item) => (
          <a
            key={item.id}
            href={`#${item.id}`}
            onClick={() => onSelect(item.id)}
            className={[
              'block px-3 py-2 rounded-md text-button transition-colors',
              active === item.id
                ? 'bg-sage-100 text-fg-strong'
                : 'text-fg-muted hover:bg-sage-50 hover:text-fg',
            ].join(' ')}
          >
            {item.label}
          </a>
        ))}
      </nav>
      <div className="mt-10 pt-5 border-t border-border space-y-2 text-caption text-fg-muted">
        <div className="flex items-center gap-2">
          <span className="inline-block w-2.5 h-2.5 rounded-pill bg-sage-700" />
          NEW (Tailwind v4)
        </div>
        <div className="flex items-center gap-2">
          <span
            className="inline-block w-2.5 h-2.5 rounded-pill"
            style={{ backgroundColor: '#1D4ED8' }}
          />
          OLD (design-system.md)
        </div>
      </div>
    </aside>
  )
}

function PageHeader() {
  return (
    <header className="mb-16">
      <div className="text-button text-fg-muted uppercase tracking-wider mb-3">
        frontend / app/styles
      </div>
      <h1 className="font-display text-display-mobile lg:text-display uppercase tracking-tight text-fg-strong">
        Design Tokens
      </h1>
      <p className="mt-5 text-rich text-fg-muted max-w-2xl">
        새로 정의한 Tailwind v4 디자인 시스템과{' '}
        <code className="font-mono text-button text-fg">
          docs/design-system.md
        </code>{' '}
        의 기존 스펙을 토큰 단위로 비교한다.
      </p>
    </header>
  )
}

function Section({
  id,
  label,
  title,
  description,
  children,
}: {
  id: SectionId
  label: string
  title: string
  description: string
  children: ReactNode
}) {
  return (
    <section id={id} className="mb-20 scroll-mt-10">
      <header className="mb-8 pb-5 border-b border-border-strong">
        <div className="text-button text-fg-muted uppercase tracking-wider mb-2">
          {label}
        </div>
        <h2 className="font-heading text-h3 uppercase tracking-tight text-fg-strong">
          {title}
        </h2>
        <p className="mt-3 text-rich text-fg-muted max-w-3xl">{description}</p>
      </header>
      {children}
    </section>
  )
}

function CompareRow({ children }: { children: ReactNode }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">{children}</div>
  )
}

function Panel({
  tone,
  label,
  sublabel,
  children,
}: {
  tone: 'new' | 'old'
  label: string
  sublabel?: string
  children: ReactNode
}) {
  return (
    <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
      <div
        className={[
          'flex items-baseline justify-between gap-3 px-4 py-2.5 text-caption uppercase tracking-wider font-semibold text-white',
          tone === 'new' ? 'bg-sage-700' : '',
        ].join(' ')}
        style={tone === 'old' ? { backgroundColor: '#1D4ED8' } : undefined}
      >
        <span>{label}</span>
        {sublabel && (
          <span className="opacity-70 normal-case tracking-normal font-mono">
            {sublabel}
          </span>
        )}
      </div>
      <div className="p-5">{children}</div>
    </div>
  )
}

function MutedLabel({ children }: { children: ReactNode }) {
  return (
    <div className="text-caption text-fg-muted uppercase tracking-wider mb-2 font-semibold">
      {children}
    </div>
  )
}

function Swatch({
  token,
  hex,
  note,
}: {
  token: string
  hex: string
  note?: string
}) {
  return (
    <div className="rounded-md overflow-hidden border border-border min-w-0">
      <div
        className="h-20 flex flex-col justify-end p-2 font-mono text-caption"
        style={{ backgroundColor: hex, color: readableFg(hex) }}
      >
        {hex.toUpperCase()}
      </div>
      <div className="px-3 py-2 bg-surface-raised">
        <div className="text-button font-semibold text-fg truncate">
          {token}
        </div>
        {note && (
          <div className="text-caption text-fg-muted truncate">{note}</div>
        )}
      </div>
    </div>
  )
}

function KeyValueRow({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-4 border-b border-border last:border-0 py-1.5">
      <dt className="text-fg-muted">{k}</dt>
      <dd className="text-fg-strong text-right">{v}</dd>
    </div>
  )
}

// =========================================================
// Sections
// =========================================================
function OverviewSection() {
  return (
    <Section
      id="overview"
      label="01 Overview"
      title="비교 개요"
      description="좌 — 본 PR에서 정의한 Tailwind v4 토큰. 우 — docs/design-system.md 의 기존 스펙."
    >
      <CompareRow>
        <Panel tone="new" label="NEW · Tailwind v4" sublabel="tokens.css">
          <dl>
            <KeyValueRow k="컬러" v="Sage / Warm Gray (모노크로매틱)" />
            <KeyValueRow k="브랜드 톤" v="차분 · 진중 · 면접 친화" />
            <KeyValueRow k="Heading" v="Fira Sans Extra Condensed" />
            <KeyValueRow k="Subheading" v="Geist" />
            <KeyValueRow k="Body" v="Inter" />
            <KeyValueRow k="Display H1" v="100px / 모바일 48px" />
          </dl>
        </Panel>
        <Panel tone="old" label="OLD · design-system.md" sublabel="§2">
          <dl style={PRETENDARD}>
            <KeyValueRow k="컬러" v="Blue Brand + 4-tone Semantic" />
            <KeyValueRow k="브랜드 톤" v="신뢰감 (블루 계열)" />
            <KeyValueRow k="Heading" v="Pretendard Variable" />
            <KeyValueRow k="Subheading" v="Pretendard Variable" />
            <KeyValueRow k="Body" v="Pretendard Variable" />
            <KeyValueRow k="Display" v="48px (반응형 미정)" />
          </dl>
        </Panel>
      </CompareRow>
    </Section>
  )
}

function ColorsSection() {
  return (
    <Section
      id="colors"
      label="02 Colors"
      title="컬러 시스템"
      description="새 시스템은 단일 Sage 스케일(11단계) 기반 모노크로매틱. 기존 시스템은 Blue Brand + 4-tone Semantic + 직군 컬러 8종."
    >
      <div className="space-y-10">
        <div>
          <h3 className="font-subheading text-h6 font-bold text-fg-strong mb-3">
            Sage Scale (NEW · 11단계)
          </h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 xl:grid-cols-11 gap-3">
            {SAGE_SCALE.map((c) => (
              <Swatch key={c.token} token={c.token} hex={c.hex} />
            ))}
          </div>
        </div>

        <CompareRow>
          <Panel tone="new" label="NEW · Base + Semantic">
            <MutedLabel>Base</MutedLabel>
            <div className="grid grid-cols-3 gap-3 mb-5">
              {NEW_BASE.map((c) => (
                <Swatch key={c.token} token={c.token} hex={c.hex} />
              ))}
            </div>
            <MutedLabel>Semantic Aliases</MutedLabel>
            <div className="grid grid-cols-2 gap-3">
              {NEW_SEMANTIC.map((c) => (
                <Swatch
                  key={c.token}
                  token={c.token}
                  hex={c.hex}
                  note={c.alias ? `→ ${c.alias}` : undefined}
                />
              ))}
            </div>
          </Panel>
          <Panel tone="old" label="OLD · Brand + Semantic + Neutral">
            <MutedLabel>Brand</MutedLabel>
            <div className="grid grid-cols-2 gap-3 mb-5">
              {OLD_BRAND.map((c) => (
                <Swatch key={c.token} token={c.token} hex={c.hex} />
              ))}
            </div>
            <MutedLabel>Semantic</MutedLabel>
            <div className="grid grid-cols-2 gap-3 mb-5">
              {OLD_SEMANTIC.map((c) => (
                <Swatch key={c.token} token={c.token} hex={c.hex} />
              ))}
            </div>
            <MutedLabel>Neutral</MutedLabel>
            <div className="grid grid-cols-2 gap-3">
              {OLD_NEUTRAL.map((c) => (
                <Swatch key={c.token} token={c.token} hex={c.hex} />
              ))}
            </div>
          </Panel>
        </CompareRow>

        <div>
          <h3 className="font-subheading text-h6 font-bold text-fg-strong mb-1">
            Domain Colors{' '}
            <span className="text-button text-fg-muted font-normal">
              (OLD only — 새 시스템 매핑 미결)
            </span>
          </h3>
          <p className="text-button text-fg-muted mb-3">
            직군 / 면접 유형 컬러는 docs/design-system.md §2.1 에만 존재. Sage
            모노크로매틱과 어떻게 매핑할지 별도 결정 필요.
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
            {OLD_DOMAIN.map((c) => (
              <Swatch key={c.token} token={c.token} hex={c.hex} />
            ))}
          </div>
        </div>
      </div>
    </Section>
  )
}

function TypographySection() {
  const SAMPLE = 'StackUp · 빠른 면접, 깊은 피드백'
  return (
    <Section
      id="typography"
      label="03 Typography"
      title="타이포그래피"
      description="Heading + Subheading + Body 의 3-Tier Sans (NEW) vs 단일 Pretendard (OLD). 헤딩의 비주얼 임팩트가 핵심 차이."
    >
      <CompareRow>
        <Panel
          tone="new"
          label="NEW · 3-Tier Sans"
          sublabel="Fira / Geist / Inter"
        >
          <ul className="space-y-5">
            {NEW_TYPE.map((t) => (
              <li
                key={t.token}
                className="border-b border-border last:border-0 pb-4 last:pb-0"
              >
                <div className={`${t.className} text-fg-strong`}>{SAMPLE}</div>
                <div className="mt-2 text-caption text-fg-muted font-mono flex flex-wrap gap-x-3 gap-y-0.5">
                  <span className="text-fg">{t.token}</span>
                  <span>{t.family}</span>
                  <span>{t.size}</span>
                  <span>w{t.weight}</span>
                </div>
              </li>
            ))}
          </ul>
        </Panel>
        <Panel tone="old" label="OLD · Single Family" sublabel="Pretendard">
          <ul className="space-y-5">
            {OLD_TYPE.map((t) => (
              <li
                key={t.token}
                className="border-b border-border last:border-0 pb-4 last:pb-0"
              >
                <div style={t.style} className="text-fg-strong">
                  {SAMPLE}
                </div>
                <div className="mt-2 text-caption text-fg-muted font-mono flex flex-wrap gap-x-3 gap-y-0.5">
                  <span className="text-fg">{t.token}</span>
                  <span>{t.family}</span>
                  <span>{t.size}</span>
                  <span>w{t.weight}</span>
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      </CompareRow>
    </Section>
  )
}

function RadiusSection() {
  const items = [
    { token: 'sm', value: '4px', cls: 'rounded-sm' },
    { token: 'md', value: '8px', cls: 'rounded-md' },
    { token: 'lg', value: '12px', cls: 'rounded-lg' },
    { token: 'xl', value: '16px', cls: 'rounded-xl' },
    { token: '2xl', value: '24px', cls: 'rounded-2xl' },
    { token: 'pill', value: '9999px', cls: 'rounded-pill' },
  ]
  return (
    <Section
      id="radius"
      label="04 Radius"
      title="라디우스"
      description="값 자체는 양쪽 모두 4 / 8 / 12 / 16 / pill 동일. 새 시스템에서 2xl(24px) 한 단계가 추가됨."
    >
      <CompareRow>
        <Panel tone="new" label="NEW · rounded-*">
          <div className="grid grid-cols-3 gap-4">
            {items.map((r) => (
              <div key={r.token} className="text-center">
                <div
                  className={`h-20 bg-sage-200 border border-sage-300 mb-2 ${r.cls}`}
                />
                <div className="text-button font-semibold">{r.cls}</div>
                <div className="text-caption text-fg-muted font-mono">
                  {r.value}
                </div>
              </div>
            ))}
          </div>
        </Panel>
        <Panel tone="old" label="OLD · --radius-*">
          <dl>
            <KeyValueRow k="--radius-sm" v="4px" />
            <KeyValueRow k="--radius-md" v="8px" />
            <KeyValueRow k="--radius-lg" v="12px" />
            <KeyValueRow k="--radius-xl" v="16px" />
            <KeyValueRow k="--radius-pill" v="9999px" />
          </dl>
          <div className="mt-4 text-caption text-fg-muted">
            동일 5단계. 새 시스템에 <code className="font-mono">2xl</code>{' '}
            (24px) 추가됨.
          </div>
        </Panel>
      </CompareRow>
    </Section>
  )
}

function ShadowSample({ name, cls }: { name: string; cls: string }) {
  return (
    <div className="flex items-center gap-4">
      <div className={`w-20 h-12 bg-white rounded-md ${cls}`} />
      <div className="text-button font-mono">{name}</div>
    </div>
  )
}

function ShadowSection() {
  return (
    <Section
      id="shadow"
      label="05 Shadow"
      title="섀도우 / Elevation"
      description="새 시스템은 그림자 색상에 sage tint(rgba 31,39,27)를 사용. 기존은 순수 black(rgba 0,0,0). 면 전체의 톤 일관성에서 차이."
    >
      <CompareRow>
        <Panel tone="new" label="NEW · shadow-*">
          <div className="space-y-5 bg-surface p-5 rounded-md">
            <ShadowSample name="shadow-sm" cls="shadow-sm" />
            <ShadowSample name="shadow-md" cls="shadow-md" />
            <ShadowSample name="shadow-lg" cls="shadow-lg" />
            <ShadowSample name="shadow-focus-ring" cls="shadow-focus-ring" />
          </div>
        </Panel>
        <Panel tone="old" label="OLD · --shadow-*">
          <div
            className="space-y-5 p-5 rounded-md"
            style={{ backgroundColor: '#F9FAFB' }}
          >
            <ShadowSample
              name="shadow-sm"
              cls="shadow-[0_1px_2px_rgba(0,0,0,0.06)]"
            />
            <ShadowSample
              name="shadow-md"
              cls="shadow-[0_4px_6px_-1px_rgba(0,0,0,0.10),0_2px_4px_-2px_rgba(0,0,0,0.06)]"
            />
            <ShadowSample
              name="shadow-lg"
              cls="shadow-[0_10px_15px_-3px_rgba(0,0,0,0.10),0_4px_6px_-4px_rgba(0,0,0,0.05)]"
            />
            <ShadowSample
              name="shadow-focus-ring"
              cls="shadow-[0_0_0_3px_rgba(37,99,235,0.45)]"
            />
          </div>
        </Panel>
      </CompareRow>
    </Section>
  )
}

function SpacingSection() {
  const scale = [1, 2, 3, 4, 5, 6, 8, 10, 12, 16]
  return (
    <Section
      id="spacing"
      label="06 Spacing"
      title="간격 (4px Grid)"
      description="양 시스템 모두 동일한 4px 그리드. Tailwind v4 기본 spacing 스케일을 그대로 사용 (n × 4px)."
    >
      <Panel tone="new" label="Spacing Scale" sublabel="space-{n}">
        <div className="space-y-2">
          {scale.map((n) => (
            <div key={n} className="flex items-center gap-4">
              <div className="w-16 text-button font-mono text-fg-muted">
                space-{n}
              </div>
              <div
                className="bg-sage-500 h-3"
                style={{ width: `${n * 4}px` }}
              />
              <div className="text-caption text-fg-muted font-mono">
                {n * 4}px
              </div>
            </div>
          ))}
        </div>
      </Panel>
    </Section>
  )
}

function MotionDemo() {
  const [hover, setHover] = useState(false)
  return (
    <div
      className="bg-sage-50 border border-sage-200 rounded-md p-3 cursor-pointer"
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
    >
      <div
        className="h-10 bg-sage-500 rounded-md"
        style={{
          width: hover ? '100%' : '20%',
          transitionProperty: 'width',
          transitionDuration: 'var(--duration-normal)',
          transitionTimingFunction: 'var(--ease-standard)',
        }}
      />
      <div className="mt-2 text-caption text-fg-muted">
        hover to play (200ms · ease-standard)
      </div>
    </div>
  )
}

function MotionSection() {
  return (
    <Section
      id="motion"
      label="07 Motion"
      title="모션"
      description="duration / easing 정의. global.css 의 prefers-reduced-motion 시 0ms 강제."
    >
      <CompareRow>
        <Panel tone="new" label="NEW · --duration-*, --ease-*">
          <dl>
            <KeyValueRow k="duration-fast" v="120ms" />
            <KeyValueRow k="duration-normal" v="200ms" />
            <KeyValueRow k="duration-slow" v="320ms" />
            <KeyValueRow k="ease-standard" v="cubic-bezier(0.4, 0, 0.2, 1)" />
            <KeyValueRow k="ease-decelerate" v="cubic-bezier(0, 0, 0.2, 1)" />
            <KeyValueRow k="ease-accelerate" v="cubic-bezier(0.4, 0, 1, 1)" />
          </dl>
          <div className="mt-5">
            <MutedLabel>Live Preview</MutedLabel>
            <MotionDemo />
          </div>
        </Panel>
        <Panel tone="old" label="OLD · --duration-*, --easing-*">
          <dl>
            <KeyValueRow k="duration-fast" v="120ms" />
            <KeyValueRow k="duration-normal" v="200ms" />
            <KeyValueRow k="duration-slow" v="320ms" />
            <KeyValueRow k="easing-standard" v="cubic-bezier(0.4, 0, 0.2, 1)" />
            <KeyValueRow k="easing-decelerate" v="cubic-bezier(0, 0, 0.2, 1)" />
            <KeyValueRow k="easing-accelerate" v="cubic-bezier(0.4, 0, 1, 1)" />
          </dl>
          <div className="mt-4 text-caption text-fg-muted">
            값 동일. 키 명만 <code className="font-mono">--easing-*</code> →{' '}
            <code className="font-mono">--ease-*</code> 로 단순화.
          </div>
        </Panel>
      </CompareRow>
    </Section>
  )
}

// =========================================================
// App
// =========================================================
export default function App() {
  const [active, setActive] = useState<SectionId>('overview')

  return (
    <div className="min-h-screen bg-bg text-fg font-sans">
      <div className="flex">
        <Sidebar active={active} onSelect={setActive} />
        <main className="flex-1 min-w-0">
          <div className="max-w-[1280px] px-6 lg:px-12 py-12">
            <PageHeader />
            <OverviewSection />
            <ColorsSection />
            <TypographySection />
            <RadiusSection />
            <ShadowSection />
            <SpacingSection />
            <MotionSection />
          </div>
        </main>
      </div>
    </div>
  )
}
