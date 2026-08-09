import { Reveal } from '@/shared/ui'
import { FollowupVisual, ScoringVisual } from './FeatureVisuals'

const rows = [
  {
    eyebrow: '꼬리질문',
    title: '답을 흐리면 그 지점을 다시 묻습니다',
    line: '4개 축으로 채점하고, 가장 낮게 나온 축을 겨냥합니다.',
    visual: <FollowupVisual />,
  },
  {
    eyebrow: '근거 있는 채점',
    title: '왜 그 점수인지 자료로 확인합니다',
    line: '근거를 못 찾으면 추측하지 않고 비웁니다.',
    visual: <ScoringVisual />,
  },
]

/** 라벨 좌측 · 내용 우측 문서형 행. 헤어라인으로만 끊고 카드·큰 여백을 쓰지 않는다. */
export function HomeFeatures() {
  return (
    <section id="features" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 py-16 lg:px-12 lg:py-20">
        {rows.map((r, i) => (
          <Reveal key={r.eyebrow} delayMs={i * 70}>
            <div className="grid gap-6 border-t border-border py-10 lg:grid-cols-12 lg:gap-10 lg:py-12">
              <div className="lg:col-span-5">
                <span className="font-mono text-caption tracking-tight text-primary-fg">
                  {r.eyebrow}
                </span>
                <h2
                  className="mt-3 font-sans font-bold text-fg"
                  style={{
                    fontSize: 'clamp(22px, 2.2vw, 30px)',
                    lineHeight: 1.3,
                    letterSpacing: '-0.03em',
                    wordBreak: 'keep-all',
                  }}
                >
                  {r.title}
                </h2>
                <p
                  className="mt-3 max-w-sm text-body font-normal text-fg-muted"
                  style={{ wordBreak: 'keep-all' }}
                >
                  {r.line}
                </p>
              </div>
              <div className="lg:col-span-7">{r.visual}</div>
            </div>
          </Reveal>
        ))}
      </div>
    </section>
  )
}
