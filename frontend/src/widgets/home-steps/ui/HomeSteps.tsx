import type { ReactNode } from 'react'
import { Reveal } from '@/shared/ui'

function Check() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden className="h-3.5 w-3.5 shrink-0 text-primary-fg">
      <path
        d="M3.5 8.5l3 3 6-7"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function Panel({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-xl border border-border bg-surface-raised px-4 py-3.5">{children}</div>
  )
}

const steps = [
  {
    no: '01',
    title: '자료 연결',
    line: '레포·이력서·자소서를 올려두면 알아서 읽습니다.',
    panel: (
      <Panel>
        <ul className="space-y-2">
          {['이력서.pdf', 'github.com/me/pay-api', '자소서 3문항'].map((t) => (
            <li key={t} className="flex items-center gap-2 text-caption text-fg-muted">
              <Check />
              <span className="truncate">{t}</span>
              <span className="ml-auto shrink-0 font-mono text-fg-subtle">읽음</span>
            </li>
          ))}
        </ul>
      </Panel>
    ),
  },
  {
    no: '02',
    title: '맞춤 면접',
    line: '내 경험에서 나온 질문. 얕으면 다시 묻습니다.',
    panel: (
      <Panel>
        <div className="flex items-center gap-1.5">
          <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
            기술 선택
          </span>
          <span className="rounded-pill bg-primary-100 px-2 py-0.5 text-caption font-semibold text-primary-fg">
            꼬리질문
          </span>
        </div>
        <p className="mt-2.5 text-[14px] leading-relaxed text-fg-strong">
          “왜 Kafka 대신 RabbitMQ 였나요?”
        </p>
      </Panel>
    ),
  },
  {
    no: '03',
    title: '리포트',
    line: '점수 옆에 근거가 붙습니다.',
    panel: (
      <Panel>
        <ul className="space-y-2">
          {[
            { label: '종합', v: 82 },
            { label: '기술', v: 79 },
            { label: '전달력', v: 88 },
          ].map((r) => (
            <li key={r.label} className="flex items-center gap-2.5">
              <span className="w-12 shrink-0 text-caption text-fg-muted">{r.label}</span>
              <span className="h-1 flex-1 overflow-hidden rounded-pill bg-border">
                <span
                  className="block h-full rounded-pill bg-primary"
                  style={{ width: `${r.v}%` }}
                />
              </span>
              <span className="w-7 shrink-0 text-right font-mono text-caption font-semibold text-fg-strong">
                {r.v}
              </span>
            </li>
          ))}
        </ul>
      </Panel>
    ),
  },
]

/**
 * 3열 균등 카드 대신 헤어라인으로 끊는 비대칭 에디토리얼 행.
 * 섹션마다 "헤딩 → 설명 문단 → 카드 3개" 리듬이 반복되지 않게 한다.
 * 번호는 읽는 텍스트가 아니라 그래픽 요소로 크게 쓴다.
 */
export function HomeSteps() {
  return (
    <section id="how" className="bg-surface">
      <div className="mx-auto max-w-content px-6 py-24 lg:px-12 lg:py-32">
        <Reveal>
          <p className="font-mono text-caption tracking-tight text-fg-subtle">HOW IT WORKS</p>
        </Reveal>

        <ol className="mt-10">
          {steps.map((s, i) => (
            <Reveal as="li" key={s.no} delayMs={i * 70}>
              <div className="grid items-start gap-6 border-t border-border py-10 lg:grid-cols-12 lg:gap-10 lg:py-12">
                <div className="lg:col-span-5">
                  <div className="flex items-baseline gap-4">
                    <span
                      aria-hidden
                      className="font-mono font-semibold leading-none text-border-strong"
                      style={{ fontSize: 'clamp(38px, 4.4vw, 60px)', letterSpacing: '-0.04em' }}
                    >
                      {s.no}
                    </span>
                    <h3
                      className="font-sans font-bold text-fg"
                      style={{
                        fontSize: 'clamp(22px, 2.2vw, 30px)',
                        letterSpacing: '-0.03em',
                        wordBreak: 'keep-all',
                      }}
                    >
                      {s.title}
                    </h3>
                  </div>
                  <p
                    className="mt-3 max-w-xs text-body font-normal text-fg-muted"
                    style={{ wordBreak: 'keep-all' }}
                  >
                    {s.line}
                  </p>
                </div>
                <div className="lg:col-span-7">{s.panel}</div>
              </div>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  )
}
