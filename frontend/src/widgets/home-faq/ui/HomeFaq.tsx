const faqs = [
  {
    q: 'Stack Up은 무료 크레딧을 제공하나요?',
    a: '월 2회의 무료 면접 세션을 제공합니다. 학교 종합설계 프로젝트 기준이라 결제·구독 기능은 포함되지 않아요.',
  },
  {
    q: '어떤 형식의 이력서를 지원하나요?',
    a: 'Phase 1 기준 PDF만 지원합니다. HWP·DOCX는 PDF로 변환 후 업로드해 주세요.',
  },
  {
    q: 'GitHub 외 다른 로그인은 가능한가요?',
    a: '레포 분석이 핵심 기능이라, Phase 1에서는 GitHub OAuth만 제공합니다.',
  },
  {
    q: '꼬리질문은 얼마나 빨리 받을 수 있나요?',
    a: '평균 3초 이내 응답을 목표로 합니다. Flash 모델과 사전 구축 RAG 인덱스로 지연을 최소화해요.',
  },
  {
    q: '음성·비언어 분석은 어떻게 동작하나요?',
    a: 'WebRTC로 마이크·웹캠 스트림을 받아 말 속도(WPM)·간투어·시선·자세를 분석합니다. 권한을 거부하면 텍스트 입력으로 진행할 수 있어요.',
  },
]

export function HomeFaq() {
  return (
    <section id="faq" className="bg-bg">
      <div className="mx-auto max-w-content px-6 lg:px-12 pt-8 pb-24 lg:pt-12 lg:pb-32">
        <p className="text-button font-mono uppercase tracking-[0.22em] text-fg-muted">
          FAQ
        </p>

        <h2
          className="mt-4 font-heading font-extrabold uppercase text-sage-900 leading-[0.95] tracking-tight"
          style={{ fontSize: 'clamp(40px, 5vw, 64px)' }}
        >
          Questions, answered.
        </h2>

        <div className="mt-12 h-px bg-border" />

        <div className="mt-10 grid gap-12 lg:grid-cols-12">
          <div className="lg:col-span-4">
            <p className="text-rich text-fg-strong/85 leading-relaxed max-w-sm">
              여기에 없는 궁금증은 Contact 페이지에서 이어서 답해드려요.
            </p>
            <a
              href="#cta"
              className="mt-6 inline-flex items-center gap-2 pl-5 pr-2 py-2.5 rounded-pill bg-sage-800 text-white text-button hover:bg-sage-900 transition-colors duration-fast"
            >
              Contact
              <span
                aria-hidden
                className="inline-flex items-center justify-center w-6 h-6 rounded-pill bg-white/15"
              >
                →
              </span>
            </a>
          </div>

          <ul className="lg:col-span-8">
            {faqs.map((f) => (
              <li key={f.q} className="border-b border-border first:border-t">
                <details className="group">
                  <summary className="flex items-center justify-between gap-6 py-6 cursor-pointer list-none select-none">
                    <span className="text-fg-strong text-[20px] lg:text-[22px] font-medium leading-snug">
                      {f.q}
                    </span>
                    <span
                      aria-hidden
                      className="shrink-0 w-7 h-7 grid place-items-center text-fg-strong text-xl transition-transform duration-normal ease-standard group-open:rotate-45"
                    >
                      +
                    </span>
                  </summary>
                  <div className="pb-6 pr-12 text-body text-fg-strong/70 leading-relaxed max-w-3xl">
                    {f.a}
                  </div>
                </details>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
