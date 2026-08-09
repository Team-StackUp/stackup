import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'
import { Reveal } from '@/shared/ui'

export function HomeCta() {
  const getStartedTo = useGetStartedTarget()

  return (
    <section id="cta" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 pb-24 lg:px-12 lg:pb-32">
        <Reveal>
          <div className="rounded-2xl bg-primary px-6 py-16 text-center lg:px-16 lg:py-20">
            <h2
              className="font-sans font-bold text-white"
              style={{
                fontSize: 'clamp(28px, 3.4vw, 44px)',
                lineHeight: 1.25,
                letterSpacing: '-0.03em',
                wordBreak: 'keep-all',
              }}
            >
              첫 면접, 지금 시작해보세요
            </h2>
            <p
              className="mx-auto mt-4 max-w-md text-rich text-white/80"
              style={{ wordBreak: 'keep-all' }}
            >
              GitHub 계정을 연결하면 자료 분석부터 바로 시작됩니다.
            </p>

            {/* 버튼 표면이 항상 흰색이라 글자는 모드 반응 brand 텍스트(다크에서 밝아짐)가 아니라
                solid brand 를 쓴다 — 흰 배경 대비 라이트 5.9:1 · 다크 5.5:1 로 양쪽 다 AA. */}
            <Link
              to={getStartedTo}
              className="mt-9 inline-flex items-center justify-center rounded-xl bg-white px-6 py-3.5 text-body font-semibold text-primary transition-colors duration-fast hover:bg-primary-50"
            >
              GitHub으로 시작하기
            </Link>
          </div>
        </Reveal>
      </div>
    </section>
  )
}
