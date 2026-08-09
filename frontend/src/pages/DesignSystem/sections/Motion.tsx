import { useState } from 'react'
import { Code, Section } from '../primitives'

const DURATIONS = [
  { token: 'duration-fast', value: '120ms', usage: 'hover · focus' },
  { token: 'duration-normal', value: '200ms', usage: '기본 transition' },
  { token: 'duration-slow', value: '320ms', usage: '모달 enter / exit' },
]

const EASINGS = [
  { token: 'ease-standard', curve: 'cubic-bezier(0.4, 0, 0.2, 1)', usage: '기본' },
  { token: 'ease-decelerate', curve: 'cubic-bezier(0, 0, 0.2, 1)', usage: 'enter' },
  { token: 'ease-accelerate', curve: 'cubic-bezier(0.4, 0, 1, 1)', usage: 'exit' },
]

function MotionDemo() {
  const [hover, setHover] = useState(false)
  return (
    <div
      className="bg-surface border border-border rounded-md p-4 cursor-pointer select-none mt-6"
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
    >
      <div
        className="h-10 bg-primary rounded-md"
        style={{
          width: hover ? '100%' : '16%',
          transitionProperty: 'width',
          transitionDuration: 'var(--duration-normal)',
          transitionTimingFunction: 'var(--ease-standard)',
        }}
      />
      <div className="mt-2 text-caption text-fg-muted">
        hover → 200ms · ease-standard
      </div>
    </div>
  )
}

export function MotionSection() {
  return (
    <Section
      id="motion"
      label="06 MOTION"
      title="모션"
      description="prefers-reduced-motion 사용자에게는 global.css 에서 0.01ms 강제."
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
          <div className="px-4 py-2.5 bg-surface border-b border-border text-caption font-semibold uppercase tracking-wider text-fg-muted">
            Duration
          </div>
          <div className="divide-y divide-border">
            {DURATIONS.map((d) => (
              <div
                key={d.token}
                className="flex items-center justify-between px-4 py-3 gap-3"
              >
                <Code>{d.token}</Code>
                <div className="text-right">
                  <span className="text-button font-mono text-fg-strong">
                    {d.value}
                  </span>
                  <span className="ml-2 text-caption text-fg-muted">
                    {d.usage}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
          <div className="px-4 py-2.5 bg-surface border-b border-border text-caption font-semibold uppercase tracking-wider text-fg-muted">
            Easing
          </div>
          <div className="divide-y divide-border">
            {EASINGS.map((e) => (
              <div
                key={e.token}
                className="flex items-start justify-between px-4 py-3 gap-3"
              >
                <Code>{e.token}</Code>
                <div className="text-right">
                  <div className="text-caption font-mono text-fg-muted">
                    {e.curve}
                  </div>
                  <div className="text-caption text-fg-muted">{e.usage}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
      <MotionDemo />
    </Section>
  )
}
