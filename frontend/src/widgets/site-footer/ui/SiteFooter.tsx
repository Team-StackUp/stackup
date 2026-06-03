import { Link } from 'react-router-dom'
import { useGetStartedTarget } from '@/features/auth'

// 단순 뷰 섹션 widgets 에선 굳이 나누지 않는게 좋다고 판단했습니다.
// 상수, 메세지 등 마찬가지
// to: 앱 내부 라우트/해시(react-router Link). href: 외부 링크(<a> 새 탭).
type FooterLink = { label: string; to?: string; href?: string }

const columns: { title: string; links: FooterLink[] }[] = [
  {
    title: 'Company',
    links: [
      { label: 'Home', to: '/#top' },
      { label: 'About', to: '/#quote' },
      { label: 'FAQ', to: '/#faq' },
    ],
  },
  {
    title: 'Services',
    links: [
      { label: '이력서 심층 면접', to: '/sessions/new' },
      { label: '직무 기술 면접', to: '/practice/role' },
      { label: 'CS 전공 면접', to: '/practice/cs' },
    ],
  },
  {
    title: 'Resources',
    links: [
      { label: 'Workspace', to: '/workspace' },
      { label: 'Design System', to: '/design-system' },
      { label: 'GitHub', href: 'https://github.com/Team-StackUp/stackup' },
    ],
  },
]

export function SiteFooter() {
  const getStartedTo = useGetStartedTarget()
  return (
    <footer
      id="footer"
      className="relative text-white"
      style={{ background: 'var(--color-sage-800)' }}
    >
      <div className="mx-auto max-w-content px-6 lg:px-12 pt-20 lg:pt-28 pb-12">
        <div className="flex flex-col lg:flex-row lg:items-end gap-8 lg:gap-12">
          <h2
            className="font-heading font-extrabold uppercase text-white leading-[0.95] tracking-tight flex-1"
            style={{ fontSize: 'clamp(40px, 6vw, 88px)' }}
          >
            One smart step
          </h2>
          <Link
            to={getStartedTo}
            className="inline-flex self-start lg:self-end items-center gap-2 pl-5 pr-2 py-2.5 rounded-pill bg-[#e6dfd4] text-sage-900 text-button hover:bg-white transition-colors duration-fast"
          >
            Get Started
            <span
              aria-hidden
              className="inline-flex items-center justify-center w-6 h-6 rounded-pill bg-sage-900 text-white text-[11px]"
            >
              →
            </span>
          </Link>
        </div>

        <div className="mt-12 h-px bg-sage-600/70" />

        <div className="mt-12 grid gap-12 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <div className="font-heading font-extrabold uppercase tracking-[0.06em] text-white text-[22px]">
              Stack Up
            </div>
            <p className="mt-4 text-sage-200 max-w-sm leading-relaxed">
              IT 직군 멀티모달 AI 면접 시뮬레이터. GitHub 레포와 이력서를 분석해
              개인 맞춤 면접과 음성·비언어적 피드백을 제공합니다.
            </p>
          </div>

          <nav
            aria-label="Footer"
            className="lg:col-span-7 grid grid-cols-2 sm:grid-cols-3 gap-8"
          >
            {columns.map((col) => (
              <div key={col.title}>
                <div className="text-caption font-mono uppercase tracking-[0.22em] text-sage-300">
                  {col.title}
                </div>
                <ul className="mt-4 space-y-3">
                  {col.links.map((l) => (
                    <li key={l.label}>
                      {l.href ? (
                        <a
                          href={l.href}
                          target="_blank"
                          rel="noreferrer noopener"
                          className="text-white/90 hover:text-white transition-colors duration-fast"
                        >
                          {l.label}
                        </a>
                      ) : (
                        <Link
                          to={l.to ?? '/'}
                          className="text-white/90 hover:text-white transition-colors duration-fast"
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

        <div className="mt-16 pt-8 border-t border-sage-600/60 flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
          <div className="text-caption font-mono text-sage-300">
            © 2026 StackUp · CNU 종합설계. All rights reserved.
          </div>
          <ul className="flex gap-6 text-caption text-sage-300">
            <li>
              <a
                href="https://github.com/Team-StackUp/stackup"
                target="_blank"
                rel="noreferrer noopener"
                className="hover:text-white transition-colors duration-fast"
              >
                GitHub
              </a>
            </li>
            <li>
              <Link to="/design-system" className="hover:text-white transition-colors duration-fast">
                Design System
              </Link>
            </li>
          </ul>
        </div>
      </div>
    </footer>
  )
}
