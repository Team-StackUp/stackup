import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth, useLogout } from '@/features/auth'

const items = [
  { to: '/#services', label: 'Services' },
  { to: '/#quote', label: 'About' },
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
          ? 'bg-bg/85 backdrop-blur-md border-b border-border'
          : 'bg-transparent border-b border-transparent',
      ].join(' ')}
      style={{ zIndex: 'var(--z-sticky)' }}
    >
      <div className="mx-auto max-w-content px-6 lg:px-12 h-16 flex items-center justify-between">
        <Link
          to="/#top"
          className="font-heading font-extrabold tracking-[0.04em] text-sage-900 text-[15px] uppercase"
        >
          Stack Up
        </Link>

        <nav aria-label="Primary" className="hidden md:flex items-center gap-1">
          {items.map((it) => (
            <Link
              key={it.to}
              to={it.to}
              className="px-3 py-2 text-button text-fg-strong/80 hover:text-fg-strong transition-colors duration-fast"
            >
              {it.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          {status === 'authenticated' ? (
            <>
              <Link
                to="/workspace"
                className="hidden sm:inline-flex items-center gap-2 px-3 py-2 text-button text-fg-strong/80 hover:text-fg-strong transition-colors duration-fast"
              >
                {user?.avatarUrl ? (
                  <img
                    src={user.avatarUrl}
                    alt=""
                    aria-hidden
                    className="w-6 h-6 rounded-full"
                  />
                ) : null}
                <span>{user?.githubUsername ?? 'Workspace'}</span>
              </Link>
              <button
                type="button"
                onClick={logout}
                disabled={loggingOut}
                aria-busy={loggingOut}
                className="inline-flex items-center gap-2 pl-5 pr-5 py-2 rounded-pill bg-[#dbe2ec] text-sage-900 text-button hover:bg-[#c6cedd] transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {loggingOut ? '로그아웃 중…' : 'Logout'}
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="hidden sm:inline-flex items-center px-3 py-2 text-button text-fg-strong/80 hover:text-fg-strong transition-colors duration-fast"
              >
                Login
              </Link>
              <Link
                to="/login"
                className="inline-flex items-center gap-2 pl-5 pr-2 py-2 rounded-pill bg-[#dbe2ec] text-sage-900 text-button hover:bg-[#c6cedd] transition-colors duration-fast"
              >
                Get Started
                <span
                  aria-hidden
                  className="inline-flex items-center justify-center w-6 h-6 rounded-pill bg-sage-900 text-white text-[11px]"
                >
                  →
                </span>
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}
