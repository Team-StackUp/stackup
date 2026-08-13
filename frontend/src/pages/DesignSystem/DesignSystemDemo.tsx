import { useState } from 'react'
import { Eyebrow, Heading } from '@/shared/ui'
import { NAV, type SectionId } from './nav'
import { Code } from './primitives'
import {
  ColorsSection,
  ContainerSection,
  MotionSection,
  OverviewSection,
  RadiusSection,
  ShadowSection,
  SpacingSection,
  TypographySection,
  ZIndexSection,
} from './sections'

function Sidebar({
  active,
  onSelect,
}: {
  active: SectionId
  onSelect: (id: SectionId) => void
}) {
  return (
    <aside className="hidden lg:flex lg:flex-col w-56 shrink-0 sticky top-0 h-screen border-r border-border bg-surface">
      <div className="px-5 pt-6 pb-4">
        <div className="font-sans text-[17px] font-bold tracking-tight text-fg">STACK-UP</div>
        <div className="mt-1 text-caption text-fg-subtle">Design System</div>
      </div>
      <nav className="flex-1 overflow-y-auto px-3 pb-4 space-y-0.5">
        {NAV.map((item) => {
          const isActive = active === item.id
          return (
            <a
              key={item.id}
              href={`#${item.id}`}
              onClick={() => onSelect(item.id)}
              className={[
                'relative block pl-4 pr-3 py-2 rounded-md text-button transition-colors duration-fast',
                isActive
                  ? 'bg-surface text-fg-strong font-semibold'
                  : 'text-fg-muted hover:bg-surface hover:text-fg',
              ].join(' ')}
            >
              {isActive && (
                <span className="absolute left-1 top-1/2 -translate-y-1/2 w-0.5 h-4 bg-primary rounded-r-sm" />
              )}
              {item.label}
            </a>
          )
        })}
      </nav>
      <div className="px-5 py-4 border-t border-border text-caption text-fg-muted font-mono">
        Tailwind CSS v4
      </div>
    </aside>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border bg-surface-raised px-5 py-4 transition-colors duration-fast hover:border-border-strong">
      <dt className="mb-2 font-mono text-caption tracking-tight text-fg-subtle">{label}</dt>
      <dd
        className="font-sans font-bold leading-none text-fg"
        style={{ fontSize: '22px', letterSpacing: '-0.03em' }}
      >
        {value}
      </dd>
    </div>
  )
}

function Hero() {
  return (
    <header className="mb-20">
      <Eyebrow className="mb-4">frontend / app / styles / tokens.css</Eyebrow>
      <Heading level="page" as="h1">
        Design System
      </Heading>
      <p className="mt-6 max-w-2xl text-rich font-normal text-fg-muted">
        STACK-UP 프론트엔드의 시각·행동 규약. <Code>tokens.css</Code> 의{' '}
        <Code>@theme</Code> 블록을 SSOT 로 한 Tailwind v4 토큰 시스템.
      </p>

      <dl className="mt-10 grid grid-cols-2 sm:grid-cols-4 gap-3 max-w-3xl">
        <Stat label="Color 출처" value="SEED" />
        <Stat label="Type Scale" value="12" />
        <Stat label="Sections" value="9" />
        <Stat label="Framework" value="Tailwind v4" />
      </dl>
    </header>
  )
}

export default function DesignSystemDemo() {
  const [active, setActive] = useState<SectionId>('overview')

  return (
    <div className="min-h-screen bg-bg text-fg font-sans">
      <div className="flex">
        <Sidebar active={active} onSelect={setActive} />
        <main className="flex-1 min-w-0">
          <div className="max-w-content px-6 lg:px-12 py-12">
            <Hero />
            <OverviewSection />
            <ColorsSection />
            <TypographySection />
            <RadiusSection />
            <ShadowSection />
            <SpacingSection />
            <MotionSection />
            <ZIndexSection />
            <ContainerSection />
          </div>
        </main>
      </div>
    </div>
  )
}
