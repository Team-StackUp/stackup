import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'
import { Reveal } from '@/shared/ui'
import { HeroPreview } from './HeroPreview'

/** 설명 문단 대신 숫자로 말한다. 값은 실제 구현과 일치. */
const facts = [
  { v: '4', unit: '축', label: '답변 채점' },
  { v: '3', unit: '명', label: '면접관 패널' },
  { v: '2', unit: '회', label: '월 무료' },
]

export function HomeHero() {
  const getStartedTo = useGetStartedTarget()

  return (
    <section id="top" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 pt-12 pb-16 lg:px-12 lg:pt-16 lg:pb-20">
        <div className="grid items-center gap-12 lg:grid-cols-12 lg:gap-10">
          <div className="lg:col-span-6">
            <Reveal>
              <h1
                className="font-sans font-bold text-fg"
                style={{
                  fontSize: 'clamp(32px, 4.2vw, 52px)',
                  lineHeight: 1.2,
                  letterSpacing: '-0.035em',
                  wordBreak: 'keep-all',
                }}
              >
                내 이력서를 아는
                <br />
                면접관과 연습하세요
              </h1>
            </Reveal>

            <Reveal delayMs={60}>
              <p
                className="mt-5 max-w-md text-rich text-fg-muted"
                style={{ wordBreak: 'keep-all' }}
              >
                자료에서 질문을 만들고, 얕은 답은 다시 묻습니다.
              </p>
            </Reveal>

            <Reveal delayMs={120}>
              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Link
                  to={getStartedTo}
                  className="inline-flex items-center justify-center rounded-xl bg-primary px-6 py-3.5 text-body font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
                >
                  GitHub으로 시작하기
                </Link>
                <a
                  href="#how"
                  className="inline-flex items-center gap-1 px-1 py-3.5 text-body font-semibold text-fg-strong transition-colors duration-fast hover:text-primary-fg"
                >
                  동작 방식 보기
                  <span aria-hidden>→</span>
                </a>
              </div>
            </Reveal>

            <Reveal delayMs={180}>
              <dl className="mt-10 flex divide-x divide-border border-t border-border pt-6">
                {facts.map((f) => (
                  <div key={f.label} className="flex-1 pr-4 first:pl-0 [&:not(:first-child)]:pl-5">
                    <dd className="flex items-baseline gap-1">
                      <span
                        className="font-sans font-bold text-fg"
                        style={{ fontSize: '26px', letterSpacing: '-0.04em' }}
                      >
                        {f.v}
                      </span>
                      <span className="text-button font-normal text-fg-muted">{f.unit}</span>
                    </dd>
                    <dt className="mt-1 text-caption text-fg-subtle">{f.label}</dt>
                  </div>
                ))}
              </dl>
            </Reveal>
          </div>

          <Reveal delayMs={140} className="lg:col-span-6">
            <HeroPreview />
          </Reveal>
        </div>
      </div>
    </section>
  )
}
