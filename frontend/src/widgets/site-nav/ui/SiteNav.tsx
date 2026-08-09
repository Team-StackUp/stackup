import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth, useLogout } from '@/features/auth'
import { useColorMode } from '@/shared/lib/color-mode'

function ColorModeToggle() {
  const { isDark, toggle } = useColorMode()
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      title={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong"
    >
      {isDark ? (
        // sun
        <svg viewBox="0 0 24 24" aria-hidden className="h-[18px] w-[18px]">
          <circle cx="12" cy="12" r="4.5" fill="currentColor" />
          <g stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
            <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M18.7 5.3l-1.4 1.4M6.7 17.3l-1.4 1.4" />
          </g>
        </svg>
      ) : (
        // moon
        <svg viewBox="0 0 24 24" aria-hidden className="h-[18px] w-[18px]">
          <path
            d="M20 14.2A8.2 8.2 0 0 1 9.8 4a8.5 8.5 0 1 0 10.2 10.2Z"
            fill="currentColor"
          />
        </svg>
      )}
    </button>
  )
}

const items = [
  { to: '/#features', label: '기능' },
  { to: '/#how', label: '동작 방식' },
  { to: '/#faq', label: 'FAQ' },
]

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false)
  const { status, user } = useAuth()
  const { logout, loggingOut } = useLogout()

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <header
      className={[
        'sticky top-0 w-full transition-colors duration-normal ease-standard',
        scrolled
          ? 'border-b border-border bg-surface-raised/85 backdrop-blur-md'
          : 'border-b border-transparent bg-transparent',
      ].join(' ')}
      style={{ zIndex: 'var(--z-sticky)' }}
    >
      <div className="mx-auto flex h-16 max-w-content items-center justify-between px-6 lg:px-12">
        <Link
          to="/#top"
          className="font-sans text-[17px] font-bold tracking-tight text-fg"
        >
          STACK-UP
        </Link>

        <nav aria-label="Primary" className="hidden items-center gap-1 md:flex">
          {items.map((it) => (
            <Link
              key={it.to}
              to={it.to}
              className="rounded-md px-3 py-2 text-button text-fg-muted transition-colors duration-fast hover:text-fg-strong"
            >
              {it.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-1.5">
          <ColorModeToggle />
          {status === 'authenticated' ? (
            <>
              <Link
                to="/workspace"
                className="hidden items-center gap-2 rounded-md px-3 py-2 text-button text-fg-muted transition-colors duration-fast hover:text-fg-strong sm:inline-flex"
              >
                {user?.avatarUrl ? (
                  <img src={user.avatarUrl} alt="" aria-hidden className="h-6 w-6 rounded-full" />
                ) : null}
                <span>{user?.githubUsername ?? '워크스페이스'}</span>
              </Link>
              <button
                type="button"
                onClick={logout}
                disabled={loggingOut}
                aria-busy={loggingOut}
                className="inline-flex items-center rounded-lg border border-border-strong px-4 py-2 text-button text-fg-strong transition-colors duration-fast hover:bg-surface disabled:cursor-not-allowed disabled:opacity-60"
              >
                {loggingOut ? '로그아웃 중…' : '로그아웃'}
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="hidden items-center rounded-md px-3 py-2 text-button text-fg-muted transition-colors duration-fast hover:text-fg-strong sm:inline-flex"
              >
                로그인
              </Link>
              <Link
                to="/login"
                className="inline-flex items-center rounded-lg bg-primary px-4 py-2.5 text-button text-white transition-colors duration-fast hover:bg-primary-hover"
              >
                시작하기
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
