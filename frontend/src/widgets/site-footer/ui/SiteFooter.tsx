import { Link } from 'react-router-dom'

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

export function SiteFooter() {
  return (
    <footer id="footer" className="bg-sage-900 text-white">
      <div className="mx-auto max-w-content px-6 pt-16 pb-12 lg:px-12 lg:pt-20">
        <div className="grid gap-12 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <div className="font-sans text-[19px] font-bold tracking-tight text-white">
              Stack Up
            </div>
            <p
              className="mt-4 max-w-sm text-body font-normal leading-relaxed text-sage-200"
              style={{ wordBreak: 'keep-all' }}
            >
              이력서·자소서·GitHub 레포를 읽고 나에게 맞는 모의 면접을 진행합니다. 답변의 근거와
              전달력까지 담긴 리포트를 받아보세요.
            </p>
          </div>

          <nav
            aria-label="Footer"
            className="grid grid-cols-2 gap-8 sm:grid-cols-3 lg:col-span-7"
          >
            {columns.map((col) => (
              <div key={col.title}>
                <div className="text-caption font-semibold text-sage-300">{col.title}</div>
                <ul className="mt-4 space-y-3">
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

        <div className="mt-16 flex flex-col gap-3 border-t border-sage-700 pt-8 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-caption text-sage-300">© 2026 StackUp. All rights reserved.</div>
          <ul className="flex gap-6 text-caption text-sage-300">
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
