export function HomeQuote() {
  return (
    <section id="quote" className="bg-bg">
      <div className="mx-auto max-w-content px-6 lg:px-12 pb-24 lg:pb-32">
        <div
          className="relative overflow-hidden rounded-2xl"
          style={{ background: 'var(--color-sage-800)' }}
        >
          <div className="grid lg:grid-cols-12 min-h-[420px]">
            {/* decorative left panel */}
            <div className="relative lg:col-span-5 min-h-[260px] lg:min-h-0 overflow-hidden">
              <div
                aria-hidden
                className="absolute inset-0"
                style={{
                  background:
                    'radial-gradient(120% 80% at 20% 30%, #2b3a59 0%, #1e2a44 60%, #101627 100%)',
                }}
              />
              <div
                aria-hidden
                className="absolute inset-0 opacity-[0.35] mix-blend-screen"
                style={{
                  background:
                    'radial-gradient(40% 35% at 70% 25%, #c6cedd 0%, transparent 60%),' +
                    'radial-gradient(50% 40% at 30% 80%, #4a5a7e 0%, transparent 65%)',
                }}
              />
              <div className="relative h-full flex items-end p-8 lg:p-10">
                <div
                  className="font-script text-white/85"
                  style={{
                    fontFamily: 'var(--font-script)',
                    fontSize: 'clamp(64px, 8vw, 120px)',
                    fontWeight: 700,
                    lineHeight: 0.9,
                    transform: 'rotate(-3deg)',
                  }}
                >
                  Stack Up
                </div>
              </div>
            </div>

            {/* quote panel */}
            <div className="lg:col-span-7 p-8 lg:p-14 flex flex-col">
              <div
                aria-hidden
                className="font-script text-sage-300/40 leading-none"
                style={{
                  fontFamily: 'var(--font-script)',
                  fontSize: 'clamp(80px, 9vw, 140px)',
                  fontWeight: 700,
                }}
              >
                “
              </div>

              <blockquote className="-mt-6">
                <p className="text-white text-[24px] lg:text-[30px] leading-snug font-medium">
                  Stack Up은 직군을 가리지 않는 올인원 모의 면접 솔루션입니다. 이력서·자소서·
                  GitHub 레포에서 출발해, 답변의 깊이까지 따라 들어갑니다.
                </p>
              </blockquote>

              <div className="mt-auto pt-12">
                <div className="text-sage-200 text-button font-medium">
                  전 직군 · 맞춤 질문 · 음성·비언어 분석까지
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
