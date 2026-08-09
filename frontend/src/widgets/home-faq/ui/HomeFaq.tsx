import { Reveal } from '@/shared/ui'

const faqs = [
  {
    q: 'STACK-UP은 무료 크레딧을 제공하나요?',
    a: '월 2회 무료 면접 세션을 제공합니다. 현재 결제·구독 기능은 없습니다.',
  },
  {
    q: '어떤 형식의 이력서를 지원하나요?',
    a: '현재 PDF만 지원합니다. HWP·DOCX는 PDF로 변환 후 업로드해 주세요. (자소서는 문항별 텍스트로 바로 입력할 수 있어요.)',
  },
  {
    q: 'GitHub 외 다른 로그인은 가능한가요?',
    a: '레포 분석이 핵심이라 지금은 GitHub OAuth만 지원합니다.',
  },
  {
    q: '꼬리질문은 얼마나 빨리 받을 수 있나요?',
    a: '평균 3초 이내를 목표로 합니다. 저지연 모델과 사전 구축한 검색 인덱스를 씁니다.',
  },
  {
    q: '음성 분석은 어떻게 동작하나요?',
    a: '마이크 스트림을 받아 말 속도(어절/분)·무음 구간·간투어를 측정합니다. 권한을 거부하면 텍스트 입력으로 진행할 수 있어요.',
  },
]

export function HomeFaq() {
  return (
    <section id="faq" className="bg-surface-raised">
      <div className="mx-auto max-w-content px-6 py-16 lg:px-12 lg:py-20">
        {/* 헤딩은 다른 섹션과 같은 좌측 거터에 맞추고, 목록만 읽기 좋은 폭으로 제한. */}
        <div className="max-w-3xl">
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
              자주 묻는 질문
            </h2>
          </Reveal>

          <ul className="mt-8">
            {faqs.map((f, i) => (
              <Reveal as="li" key={f.q} delayMs={Math.min(i, 3) * 60}>
                <div className="border-b border-border">
                  <details className="group">
                    <summary className="flex cursor-pointer list-none select-none items-center justify-between gap-6 py-6">
                      <span
                        className="text-[17px] font-medium leading-snug text-fg-strong lg:text-[19px]"
                        style={{ wordBreak: 'keep-all' }}
                      >
                        {f.q}
                      </span>
                      <span
                        aria-hidden
                        className="grid h-6 w-6 shrink-0 place-items-center text-xl text-fg-subtle transition-transform duration-normal ease-standard group-open:rotate-45"
                      >
                        +
                      </span>
                    </summary>
                    <p
                      className="max-w-2xl pb-6 pr-10 text-body font-normal leading-relaxed text-fg-muted"
                      style={{ wordBreak: 'keep-all' }}
                    >
                      {f.a}
                    </p>
                  </details>
                </div>
              </Reveal>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
