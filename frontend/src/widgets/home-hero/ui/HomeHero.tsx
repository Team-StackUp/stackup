import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'
import { Reveal } from '@/shared/ui'
import { HeroPreview } from './HeroPreview'

/** 설명 문단 대신 숫자로 말한다. 값은 실제 구현과 일치. */
const facts = [
  { v: '4', unit: '축', label: '답변 채점' },
  { v: '3', unit: '명', label: '면접관 패널' },
  { v: '4', unit: '직군', label: '맞춤 질문' },
]

export function HomeHero() {
  const getStartedTo = useGetStartedTarget()

  return (
    <section id="top" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 pt-12 pb-16 lg:px-12 lg:pt-16 lg:pb-20">
        <div className="grid items-center gap-12 lg:grid-cols-12 lg:gap-10">
          <div className="lg:col-span-6">
            <Reveal>
              <p className="font-mono text-caption tracking-tight text-fg-subtle">
                IT 면접 시뮬레이터
              </p>
              {/* 브랜드를 히어로의 주역으로 — 하이픈만 브랜드색으로 끊어 로고 락업처럼 읽히게. */}
              <h1
                className="mt-3 font-sans font-bold text-fg"
                style={{
                  fontSize: 'clamp(52px, 7.6vw, 104px)',
                  lineHeight: 0.94,
                  letterSpacing: '-0.055em',
                }}
              >
                STACK<span className="text-primary-fg">-</span>UP
              </h1>
            </Reveal>

            <Reveal delayMs={60}>
              <p
                className="mt-6 max-w-md font-sans font-bold text-fg-strong"
                style={{
                  fontSize: 'clamp(19px, 1.7vw, 23px)',
                  lineHeight: 1.4,
                  letterSpacing: '-0.03em',
                  wordBreak: 'keep-all',
                }}
              >
                내 이력서를 아는 면접관과 연습하세요
              </p>
              <p
                className="mt-2.5 max-w-md text-body font-normal text-fg-muted"
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
