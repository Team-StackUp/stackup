import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'
import { Reveal } from '@/shared/ui'
import { HeroPreview } from './HeroPreview'

const SOURCES = ['GitHub 레포', '이력서', '자소서']

export function HomeHero() {
  const getStartedTo = useGetStartedTarget()

  return (
    <section id="top" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 pt-14 pb-20 lg:px-12 lg:pt-20 lg:pb-28">
        <div className="mx-auto max-w-3xl text-center">
          <Reveal>
            <ul className="flex flex-wrap items-center justify-center gap-2">
              {SOURCES.map((s) => (
                <li
                  key={s}
                  className="rounded-pill border border-border bg-surface px-3 py-1.5 text-caption font-medium text-fg-muted"
                >
                  {s}
                </li>
              ))}
            </ul>
          </Reveal>

          <Reveal delayMs={60}>
            <h1
              className="mt-6 font-sans font-bold text-fg"
              style={{
                fontSize: 'clamp(34px, 5.4vw, 60px)',
                lineHeight: 1.18,
                letterSpacing: '-0.03em',
                wordBreak: 'keep-all',
              }}
            >
              내 이력서를 아는
              <br />
              면접관과 연습하세요
            </h1>
          </Reveal>

          <Reveal delayMs={120}>
            <p
              className="mx-auto mt-6 max-w-xl text-rich text-fg-muted"
              style={{ wordBreak: 'keep-all' }}
            >
              올려둔 자료에서 질문을 만들고, 답변이 얕은 지점을 꼬리질문으로 파고듭니다. 끝나면
              근거가 붙은 리포트를 받습니다.
            </p>
          </Reveal>

          <Reveal delayMs={180}>
            <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <Link
                to={getStartedTo}
                className="inline-flex w-full items-center justify-center rounded-xl bg-primary px-6 py-3.5 text-body font-semibold text-white transition-colors duration-fast hover:bg-primary-hover sm:w-auto"
              >
                GitHub으로 시작하기
              </Link>
              <a
                href="#how"
                className="inline-flex w-full items-center justify-center rounded-xl border border-border-strong bg-surface-raised px-6 py-3.5 text-body font-semibold text-fg-strong transition-colors duration-fast hover:bg-surface sm:w-auto"
              >
                어떻게 동작하나요?
              </a>
            </div>
          </Reveal>

          <Reveal delayMs={240}>
            <p className="mt-5 text-button font-normal text-fg-subtle">
              계정 연결만 하면 바로 시작 · 월 2회 무료
            </p>
          </Reveal>
        </div>

        <Reveal delayMs={200} className="mx-auto mt-14 max-w-2xl lg:mt-16">
          <HeroPreview />
        </Reveal>
      </div>
    </section>
  )
}
