import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'
import { useTypewriter } from '@/shared/hooks'

const TYPED_TEXT = 'Stack Up'

export function ScreenContent() {
  const { typed, done } = useTypewriter(TYPED_TEXT, {
    startDelayMs: 850,
    stepMs: 130,
  })
  const getStartedTo = useGetStartedTarget()

  return (
    <div className="relative text-center w-full">
      <p className="anim-hero-rise text-caption sm:text-button font-mono uppercase tracking-[0.28em] text-sage-700 [animation-delay:0.5s]">
        IT Interview Solution
      </p>

      <h1
        className="anim-hero-rise mt-3 sm:mt-4 text-sage-900 leading-[0.95] inline-block [animation-delay:0.8s]"
        style={{
          fontFamily: 'var(--font-script)',
          fontSize: 'clamp(56px, 8.4vw, 116px)',
          fontWeight: 700,
          letterSpacing: '-0.01em',
          transform: 'rotate(-2deg)',
          minHeight: '1em',
        }}
        aria-label={TYPED_TEXT}
      >
        <span aria-hidden>{typed}</span>
        <span
          aria-hidden
          className="inline-block align-baseline ml-1"
          style={{
            width: '3px',
            height: '0.78em',
            background: 'var(--color-sage-800)',
            verticalAlign: '-0.06em',
            animation: done ? 'caret-blink 1s steps(1, end) infinite' : 'none',
            opacity: done ? undefined : 1,
          }}
        />
      </h1>

      <div className="anim-hero-rise mt-4 sm:mt-6 [animation-delay:2.0s]">
        <Link
          to={getStartedTo}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-pill bg-sage-700 text-white text-button hover:bg-sage-800 transition-colors duration-fast"
        >
          Get Started
          <span
            aria-hidden
            className="inline-flex items-center justify-center w-5 h-5 rounded-pill bg-white/15"
          >
            →
          </span>
        </Link>
      </div>
    </div>
  )
}
