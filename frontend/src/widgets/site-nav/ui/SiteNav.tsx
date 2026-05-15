import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/features/auth'

const items = [
  { href: '#services', label: 'Services' },
  { href: '#quote', label: 'About' },
  { href: '#faq', label: 'FAQ' },
]

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false)
  const { status, user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const handleLogout = async () => {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setLoggingOut(false)
    }
  }

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
        <a
          href="#top"
          className="font-heading font-extrabold tracking-[0.04em] text-sage-900 text-[15px] uppercase"
        >
          Stack Up
        </a>

        <nav aria-label="Primary" className="hidden md:flex items-center gap-1">
          {items.map((it) => (
            <a
              key={it.href}
              href={it.href}
              className="px-3 py-2 text-button text-fg-strong/80 hover:text-fg-strong transition-colors duration-fast"
            >
              {it.label}
            </a>
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
                onClick={handleLogout}
                disabled={loggingOut}
                aria-busy={loggingOut}
                className="inline-flex items-center gap-2 pl-5 pr-5 py-2 rounded-pill bg-[#e6dfd4] text-sage-900 text-button hover:bg-[#dcd4c6] transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
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
                className="inline-flex items-center gap-2 pl-5 pr-2 py-2 rounded-pill bg-[#e6dfd4] text-sage-900 text-button hover:bg-[#dcd4c6] transition-colors duration-fast"
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
