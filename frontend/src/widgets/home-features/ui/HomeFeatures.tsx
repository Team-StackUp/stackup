import { Reveal } from '@/shared/ui'
import { FollowupVisual, ScoringVisual } from './FeatureVisuals'

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
    line: '4개 축으로 채점하고, 가장 낮게 나온 축을 겨냥합니다.',
    visual: <FollowupVisual />,
  },
  {
    eyebrow: '근거 있는 채점',
    title: '왜 그 점수인지\n자료로 확인합니다',
    line: '근거를 못 찾으면 추측하지 않고 비웁니다.',
    visual: <ScoringVisual />,
  },
]

/** 라이트 섹션 사이에 끼우는 고정 다크 블록 — 카드 그리드 반복을 끊는 역할. */
const specs = [
  { k: '전달력', v: '142', unit: '어절/분', line: '말 속도·무음·간투어를 같이 봅니다.' },
  { k: '면접관', v: '3', unit: '명', line: '직군·논리·전달을 따로 채점합니다.' },
  { k: '리포트', v: '1', unit: '링크', line: '공유하거나 PDF 로 저장합니다.' },
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
              <span className="font-mono text-caption tracking-tight text-primary-fg">
                {r.eyebrow}
              </span>
              <h2
                className="mt-3 whitespace-pre-line font-sans font-bold text-fg"
                style={headingStyle}
              >
                {r.title}
              </h2>
              <p
                className="mt-5 max-w-sm text-rich text-fg-muted"
                style={{ wordBreak: 'keep-all' }}
              >
                {r.line}
              </p>
            </Reveal>
            <Reveal delayMs={80} className={i % 2 === 1 ? 'lg:order-1' : undefined}>
              {r.visual}
            </Reveal>
          </div>
        ))}

        <Reveal className="mt-24 block lg:mt-32">
          <div className="rounded-2xl bg-sage-900 px-6 py-10 lg:px-12 lg:py-12">
            <dl className="grid gap-10 sm:grid-cols-3 sm:gap-8">
              {specs.map((s) => (
                <div key={s.k}>
                  <dt className="font-mono text-caption tracking-tight text-sage-300">{s.k}</dt>
                  <dd className="mt-3 flex items-baseline gap-1.5">
                    <span
                      className="font-sans font-bold text-white"
                      style={{ fontSize: 'clamp(32px, 3.4vw, 44px)', letterSpacing: '-0.04em' }}
                    >
                      {s.v}
                    </span>
                    <span className="text-button font-normal text-sage-300">{s.unit}</span>
                  </dd>
                  <p
                    className="mt-2 text-button font-normal leading-relaxed text-sage-200"
                    style={{ wordBreak: 'keep-all' }}
                  >
                    {s.line}
                  </p>
                </div>
              ))}
            </dl>
          </div>
        </Reveal>
      </div>
    </section>
  )
}
