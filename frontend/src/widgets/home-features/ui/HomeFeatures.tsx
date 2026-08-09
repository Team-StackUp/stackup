import { Reveal } from '@/shared/ui'
import {
  DeliveryVisual,
  FollowupVisual,
  PanelVisual,
  ReportVisual,
  ScoringVisual,
} from './FeatureVisuals'

const headingStyle = {
  fontSize: 'clamp(26px, 3vw, 38px)',
  lineHeight: 1.28,
  letterSpacing: '-0.03em',
  wordBreak: 'keep-all',
} as const

const rows = [
  {
    eyebrow: '꼬리질문',
    title: '답을 흐리면\n그 지점을 다시 묻습니다',
    desc: '답변을 구체성·논리·구조·정확성으로 채점하고, 가장 낮게 나온 축을 겨냥해 다음 질문을 만듭니다. 실제 면접에서 파고드는 방식과 같습니다.',
    visual: <FollowupVisual />,
  },
  {
    eyebrow: '근거 있는 채점',
    title: '왜 그 점수인지\n자료로 확인합니다',
    desc: '점수마다 근거 문장이 붙고, 판단의 출처가 된 이력서·레포의 대목을 그대로 보여줍니다. 근거를 못 찾으면 추측하지 않고 판단을 보류합니다.',
    visual: <ScoringVisual />,
  },
]

const cards = [
  {
    title: '전달력까지 함께',
    desc: '말 속도와 침묵, 간투어를 측정해 내용과 별개로 전달력을 코칭합니다.',
    visual: <DeliveryVisual />,
  },
  {
    title: '면접관은 여러 명',
    desc: '직군별 면접관이 각자 기준으로 채점하고, 질문 비중에 따라 종합 점수를 냅니다.',
    visual: <PanelVisual />,
  },
  {
    title: '리포트는 공유 가능',
    desc: '결과를 링크로 공유하거나 PDF로 저장해 스터디·멘토링에 그대로 씁니다.',
    visual: <ReportVisual />,
  },
]

export function HomeFeatures() {
  return (
    <section id="features" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 py-24 lg:px-12 lg:py-32">
        {rows.map((r, i) => (
          <div
            key={r.eyebrow}
            className={`grid items-center gap-10 lg:grid-cols-2 lg:gap-16 ${
              i > 0 ? 'mt-24 lg:mt-32' : ''
            }`}
          >
            <Reveal className={i % 2 === 1 ? 'lg:order-2' : undefined}>
              <span className="text-button font-semibold text-primary-fg">{r.eyebrow}</span>
              <h2 className="mt-3 whitespace-pre-line font-sans font-bold text-fg" style={headingStyle}>
                {r.title}
              </h2>
              <p
                className="mt-5 max-w-md text-rich text-fg-muted"
                style={{ wordBreak: 'keep-all' }}
              >
                {r.desc}
              </p>
            </Reveal>
            <Reveal delayMs={80} className={i % 2 === 1 ? 'lg:order-1' : undefined}>
              {r.visual}
            </Reveal>
          </div>
        ))}

        <ul className="mt-24 grid gap-5 md:grid-cols-3 lg:mt-32">
          {cards.map((c, i) => (
            <Reveal as="li" key={c.title} delayMs={i * 80}>
              <div className="flex h-full flex-col rounded-2xl border border-border bg-surface-raised p-6 lg:p-7">
                <h3 className="font-sans text-h6 text-fg">{c.title}</h3>
                <p
                  className="mt-2.5 text-body font-normal leading-relaxed text-fg-muted"
                  style={{ wordBreak: 'keep-all' }}
                >
                  {c.desc}
                </p>
                <div className="mt-auto">{c.visual}</div>
              </div>
            </Reveal>
          ))}
        </ul>
      </div>
    </section>
  )
}
