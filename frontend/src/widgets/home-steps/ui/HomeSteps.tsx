import type { ReactNode } from 'react'
import { Reveal } from '@/shared/ui'

function Check() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden className="h-3.5 w-3.5 shrink-0 text-primary">
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

/** 카드 안에서 결과물을 실제 UI 조각으로 보여주는 미니 패널. */
function Panel({ children }: { children: ReactNode }) {
  return (
    <div className="mt-6 rounded-xl border border-border bg-surface/70 px-3.5 py-3">{children}</div>
  )
}

const steps = [
  {
    no: '01',
    title: '자료 연결',
    desc: 'GitHub 레포와 이력서·자소서를 올리면 알아서 읽고 정리합니다.',
    panel: (
      <Panel>
        <ul className="space-y-2">
          {['이력서.pdf', 'github.com/me/pay-api', '자소서 3문항'].map((t) => (
            <li key={t} className="flex items-center gap-2 text-caption text-fg-muted">
              <Check />
              <span className="truncate">{t}</span>
              <span className="ml-auto shrink-0 text-fg-subtle">분석 완료</span>
            </li>
          ))}
        </ul>
      </Panel>
    ),
  },
  {
    no: '02',
    title: '맞춤 면접',
    desc: '내 경험에서 나온 질문에 답하고, 얕은 지점은 꼬리질문으로 다시 묻습니다.',
    panel: (
      <Panel>
        <div className="flex items-center gap-1.5">
          <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
            기술 선택
          </span>
          <span className="rounded-pill bg-primary-100 px-2 py-0.5 text-caption font-semibold text-primary-pressed">
            꼬리질문
          </span>
        </div>
        <p className="mt-2 text-caption leading-relaxed text-fg-muted">
          “왜 Kafka 대신 RabbitMQ 였나요?”
        </p>
      </Panel>
    ),
  },
  {
    no: '03',
    title: '리포트',
    desc: '점수와 함께 어디서 왜 깎였는지, 자료의 어느 대목이 근거인지 남습니다.',
    panel: (
      <Panel>
        <ul className="space-y-2">
          {[
            { label: '종합', v: 82 },
            { label: '기술', v: 79 },
            { label: '전달력', v: 88 },
          ].map((r) => (
            <li key={r.label} className="flex items-center gap-2.5">
              <span className="w-10 shrink-0 text-caption text-fg-muted">{r.label}</span>
              <span className="h-1.5 flex-1 overflow-hidden rounded-pill bg-border">
                <span
                  className="block h-full rounded-pill bg-primary"
                  style={{ width: `${r.v}%` }}
                />
              </span>
              <span className="w-6 shrink-0 text-right text-caption font-semibold text-fg-strong">
                {r.v}
              </span>
            </li>
          ))}
        </ul>
      </Panel>
    ),
  },
]

export function HomeSteps() {
  return (
    <section id="how" className="bg-surface">
      <div className="mx-auto max-w-content px-6 py-24 lg:px-12 lg:py-32">
        <Reveal>
          <h2
            className="font-sans font-bold text-fg"
            style={{
              fontSize: 'clamp(28px, 3.4vw, 44px)',
              lineHeight: 1.25,
              letterSpacing: '-0.03em',
              wordBreak: 'keep-all',
            }}
          >
            면접 준비는 3단계로 끝납니다
          </h2>
          <p className="mt-4 max-w-xl text-rich text-fg-muted" style={{ wordBreak: 'keep-all' }}>
            질문을 직접 고르거나 답을 미리 외울 필요가 없습니다.
          </p>
        </Reveal>

        <ol className="mt-14 grid gap-5 md:grid-cols-3">
          {steps.map((s, i) => (
            <Reveal as="li" key={s.no} delayMs={i * 80}>
              <div className="flex h-full flex-col rounded-2xl border border-border bg-surface-raised p-6 lg:p-7">
                <span className="text-button font-semibold text-primary">{s.no}</span>
                <h3 className="mt-3 font-sans text-h6 text-fg">{s.title}</h3>
                <p
                  className="mt-2.5 text-body font-normal leading-relaxed text-fg-muted"
                  style={{ wordBreak: 'keep-all' }}
                >
                  {s.desc}
                </p>
                <div className="mt-auto">{s.panel}</div>
              </div>
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  )
}
