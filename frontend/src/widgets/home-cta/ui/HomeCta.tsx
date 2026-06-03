import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'

export function HomeCta() {
  const getStartedTo = useGetStartedTarget()
  return (
    <section id="cta" className="bg-bg">
      <div className="mx-auto max-w-content px-6 lg:px-12 pb-24 lg:pb-32">
        <div className="relative overflow-hidden rounded-2xl">
          <img
            src="/last-section-get-started.png"
            alt=""
            aria-hidden
            loading="lazy"
            decoding="async"
            className="absolute inset-0 w-full h-full object-cover"
          />
          <div
            aria-hidden
            className="absolute inset-0"
            style={{
              background:
                'linear-gradient(180deg, rgba(20,26,17,0.55) 0%, rgba(20,26,17,0.35) 50%, rgba(20,26,17,0.65) 100%)',
            }}
          />

          <div className="relative min-h-[360px] lg:min-h-[440px] flex flex-col items-center justify-center text-center px-6 py-20 lg:py-28">
            <h2
              className="font-heading font-extrabold uppercase text-white leading-[0.95] tracking-tight"
              style={{ fontSize: 'clamp(40px, 5.5vw, 72px)' }}
            >
              Ready to level up?
            </h2>
            <p className="mt-5 text-white/80 text-rich max-w-xl">
              지금 GitHub 계정만 연결하면, 30초 안에 첫 모의면접이 시작됩니다.
            </p>

            <Link
              to={getStartedTo}
              className="mt-10 inline-flex items-center gap-2 pl-5 pr-2 py-2.5 rounded-pill bg-[#e6dfd4] text-sage-900 text-button hover:bg-white transition-colors duration-fast"
            >
              Get Started
              <span
                aria-hidden
                className="inline-flex items-center justify-center w-6 h-6 rounded-pill bg-sage-900 text-white text-[11px]"
              >
                →
              </span>
            </Link>
          </div>
        </div>
      </div>
    </section>
  )
}
