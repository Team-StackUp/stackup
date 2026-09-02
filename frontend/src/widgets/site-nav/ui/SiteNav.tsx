import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth, useLogout } from '@/features/auth'
import { ColorModeToggle } from '@/shared/ui'

const items = [
  { to: '/#features', label: '기능' },
  { to: '/#how', label: '동작 방식' },
  { to: '/#faq', label: 'FAQ' },
]

function MenuIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden
    >
      {open ? (
        <>
          <path d="M6 6l12 12" />
          <path d="M18 6L6 18" />
        </>
      ) : (
        <>
          <path d="M4 7h16" />
          <path d="M4 12h16" />
          <path d="M4 17h16" />
        </>
      )}
    </svg>
  )
}

export function SiteNav() {
  const [scrolled, setScrolled] = useState(false)
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const { status, user } = useAuth()
  const { logout, loggingOut } = useLogout()

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // Esc 로 닫기.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <header
      className={[
        'sticky top-0 w-full transition-colors duration-normal ease-standard',
        scrolled || open
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
                <span>{user?.displayName ?? '워크스페이스'}</span>
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
                className="inline-flex items-center rounded-lg bg-primary px-4 py-2.5 text-button text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
              >
                시작하기
              </Link>
            </>
          )}

          {/* 모바일 메뉴 토글 — 데스크톱 네비/보조 링크가 숨겨지는 구간을 보완 */}
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-expanded={open}
            aria-controls={menuId}
            aria-label={open ? '메뉴 닫기' : '메뉴 열기'}
            className="inline-flex h-9 w-9 items-center justify-center rounded-md text-fg-muted transition-colors duration-fast hover:text-fg-strong md:hidden"
          >
            <MenuIcon open={open} />
          </button>
        </div>
      </div>

      {/* 모바일 드롭다운 메뉴 */}
      {open ? (
        <nav
          id={menuId}
          aria-label="Mobile"
          className="border-t border-border bg-surface-raised/95 backdrop-blur-md md:hidden"
        >
          <div className="mx-auto flex max-w-content flex-col gap-1 px-6 py-3">
            {items.map((it) => (
              <Link
                key={it.to}
                to={it.to}
                onClick={() => setOpen(false)}
                className="rounded-md px-3 py-2.5 text-button text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong"
              >
                {it.label}
              </Link>
            ))}
            <div className="my-1 h-px bg-border" />
            {status === 'authenticated' ? (
              <Link
                to="/workspace"
                onClick={() => setOpen(false)}
                className="flex items-center gap-2 rounded-md px-3 py-2.5 text-button text-fg-strong transition-colors duration-fast hover:bg-surface"
              >
                {user?.avatarUrl ? (
                  <img src={user.avatarUrl} alt="" aria-hidden className="h-6 w-6 rounded-full" />
                ) : null}
                <span>{user?.displayName ?? '워크스페이스'}</span>
              </Link>
            ) : (
              <Link
                to="/login"
                onClick={() => setOpen(false)}
                className="rounded-md px-3 py-2.5 text-button text-fg-strong transition-colors duration-fast hover:bg-surface"
              >
                로그인
              </Link>
            )}
          </div>
        </nav>
      ) : null}
    </header>
  )
}
