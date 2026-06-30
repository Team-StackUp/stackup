import { Laptop } from './Laptop'
import { ScreenContent } from './ScreenContent'

export function HomeHero() {
  return (
    <section
      id="top"
      className="relative overflow-hidden"
      style={{
        background:
          'radial-gradient(120% 80% at 50% 0%, #f3f5fa 0%, #eceef3 60%, #eceef3 100%)',
      }}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.18] mix-blend-multiply"
        style={{
          background:
            'radial-gradient(60% 50% at 18% 30%, rgba(31,39,27,0.5) 0%, transparent 60%),' +
            'radial-gradient(40% 35% at 82% 20%, rgba(31,39,27,0.35) 0%, transparent 60%)',
        }}
      />

      <div className="relative mx-auto max-w-content px-6 lg:px-12 pt-10 pb-12 lg:pt-14 lg:pb-16 flex flex-col items-center">
        <Laptop>
          <ScreenContent />
        </Laptop>

        <p className="anim-hero-rise mt-6 lg:mt-8 text-button font-mono uppercase tracking-[0.22em] text-fg-muted [animation-delay:2.2s]">
          <span className="inline-flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-success" aria-hidden />
            Now Live · AI Based Interview
          </span>
        </p>
      </div>
    </section>
  )
}
