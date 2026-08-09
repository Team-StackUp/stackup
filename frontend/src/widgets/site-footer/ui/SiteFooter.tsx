import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'

// 단순 뷰 섹션 widgets 에선 굳이 나누지 않는게 좋다고 판단했습니다.
// 상수, 메세지 등 마찬가지
// to: 앱 내부 라우트/해시(react-router Link). href: 외부 링크(<a> 새 탭).
type FooterLink = { label: string; to?: string; href?: string }

const columns: { title: string; links: FooterLink[] }[] = [
  {
    title: '서비스',
    links: [
      { label: '이력서 심층 면접', to: '/sessions/new' },
      { label: '직무 기술 면접', to: '/practice/role' },
      { label: 'CS 전공 면접', to: '/practice/cs' },
    ],
  },
  {
    title: '둘러보기',
    links: [
      { label: '기능', to: '/#features' },
      { label: '동작 방식', to: '/#how' },
      { label: 'FAQ', to: '/#faq' },
    ],
  },
  {
    title: '리소스',
    links: [
      { label: '워크스페이스', to: '/workspace' },
      { label: '디자인 시스템', to: '/design-system' },
      { label: 'GitHub', href: 'https://github.com/Team-StackUp/stackup' },
    ],
  },
]

export type SiteFooterProps = {
  /**
   * 푸터 상단 CTA 노출 여부. 랜딩·공유 리포트처럼 방문자가 잠재 사용자인 화면에서만 켠다.
   * 이미 서비스를 쓰는 중인 화면(연습·세션·피드백)에서는 권유가 어색하다.
   */
  cta?: boolean
}

/**
 * 마지막 CTA 를 별도 섹션으로 두지 않고 푸터 상단에 흡수했다 —
 * 큰 CTA 블록 + 긴 푸터가 겹쳐 하단이 불필요하게 길어졌다.
 */
export function SiteFooter({ cta = false }: SiteFooterProps) {
  const getStartedTo = useGetStartedTarget()

  return (
    <footer id="footer" className="bg-sage-900 text-white">
      <div className="mx-auto max-w-content px-6 lg:px-12">
        {cta ? (
          <div className="flex flex-wrap items-center justify-between gap-5 border-b border-sage-700 py-10">
            <p
              className="font-sans font-bold text-white"
              style={{
                fontSize: 'clamp(22px, 2.4vw, 30px)',
                letterSpacing: '-0.03em',
                wordBreak: 'keep-all',
              }}
            >
              이제 직접 해볼 차례
            </p>
            <Link
              to={getStartedTo}
              className="inline-flex items-center justify-center rounded-xl bg-white px-5 py-3 text-button font-semibold text-primary transition-colors duration-fast hover:bg-primary-50"
            >
              GitHub으로 시작하기
            </Link>
          </div>
        ) : null}

        <div className="grid gap-10 py-12 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <div className="font-sans text-[17px] font-bold tracking-tight text-white">
              STACK-UP
            </div>
            <p
              className="mt-3 max-w-xs text-button font-normal leading-relaxed text-sage-200"
              style={{ wordBreak: 'keep-all' }}
            >
              이력서·자소서·GitHub 레포를 읽고 맞춤 모의 면접을 진행합니다.
            </p>
          </div>

          <nav aria-label="Footer" className="grid grid-cols-2 gap-8 sm:grid-cols-3 lg:col-span-7">
            {columns.map((col) => (
              <div key={col.title}>
                <div className="text-caption font-semibold text-sage-300">{col.title}</div>
                <ul className="mt-3 space-y-2.5">
                  {col.links.map((l) => (
                    <li key={l.label}>
                      {l.href ? (
                        <a
                          href={l.href}
                          target="_blank"
                          rel="noreferrer noopener"
                          className="text-button font-normal text-white/85 transition-colors duration-fast hover:text-white"
                        >
                          {l.label}
                        </a>
                      ) : (
                        <Link
                          to={l.to ?? '/'}
                          className="text-button font-normal text-white/85 transition-colors duration-fast hover:text-white"
                        >
                          {l.label}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <div className="flex flex-col gap-2 border-t border-sage-700 py-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="font-mono text-caption text-sage-300">© 2026 STACK-UP</div>
          <ul className="flex gap-5 text-caption text-sage-300">
            <li>
              <a
                href="https://github.com/Team-StackUp/stackup"
                target="_blank"
                rel="noreferrer noopener"
                className="transition-colors duration-fast hover:text-white"
              >
                GitHub
              </a>
            </li>
            <li>
              <Link
                to="/design-system"
                className="transition-colors duration-fast hover:text-white"
              >
                디자인 시스템
              </Link>
            </li>
          </ul>
        </div>
      </div>
    </footer>
  )
}
