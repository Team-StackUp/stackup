import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth, useLogout } from '@/features/auth'
import { ColorModeToggle } from '@/shared/ui'

type NavItem = {
  to: string
  label: string
  icon: ReactNode
  end?: boolean
}

const navItems: NavItem[] = [
  { to: '/workspace', label: '홈', icon: <HomeIcon />, end: true },
  { to: '/workspace/resumes', label: '이력서', icon: <ResumeIcon /> },
  { to: '/workspace/repos', label: '레포지토리', icon: <RepoIcon /> },
  { to: '/workspace/cover-letters', label: '자소서', icon: <CoverLetterIcon /> },
  { to: '/workspace/history', label: '히스토리', icon: <HistoryIcon /> },
]

export function WorkspaceSidebar() {
  const { user } = useAuth()
  const { logout, loggingOut } = useLogout()

  return (
    <aside
      className={[
        'flex flex-col border-border bg-surface-raised',
        // 모바일: 상단 가로 바. lg 이상: 좌측 고정 컬럼.
        'border-b lg:h-svh lg:w-64 lg:shrink-0 lg:border-b-0 lg:border-r',
        'lg:sticky lg:top-0',
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-2 px-6 py-5 lg:py-6">
        {/* 로고 조판은 SiteNav 와 한 글자도 다르지 않아야 한다 — 랜딩에서 워크스페이스로
            넘어올 때 같은 자리의 로고가 다른 폰트로 바뀌면 다른 서비스처럼 읽힌다. */}
        <Link to="/" className="font-sans text-[17px] font-bold tracking-tight text-fg">
          STACK-UP
        </Link>
        {/* 모바일(가로 바): 우측에 아바타 + 로그아웃. lg 이상은 하단 프로필 블록 사용. */}
        {user ? (
          <div className="flex items-center gap-1.5 lg:hidden">
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt=""
                aria-hidden
                className="h-8 w-8 rounded-full border border-border object-cover"
              />
            ) : null}
            <ColorModeToggle />
            <button
              type="button"
              onClick={logout}
              disabled={loggingOut}
              aria-busy={loggingOut}
              aria-label="로그아웃"
              className="flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong disabled:opacity-60"
            >
              <LogoutIcon />
            </button>
          </div>
        ) : null}
      </div>

      <nav
        aria-label="Workspace"
        className="flex gap-1 overflow-x-auto px-3 pb-4 lg:flex-col lg:gap-1 lg:overflow-visible lg:pb-0"
      >
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              [
                'flex shrink-0 items-center gap-2 whitespace-nowrap rounded-lg px-3 py-2.5 text-button transition-colors duration-fast lg:flex-none lg:gap-3',
                isActive
                  ? 'bg-primary-50 font-semibold text-primary-fg'
                  : 'font-medium text-fg-muted hover:bg-surface hover:text-fg-strong',
              ].join(' ')
            }
          >
            <span aria-hidden className="shrink-0">
              {item.icon}
            </span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="hidden flex-1 lg:block" />

      {user ? (
        <div className="hidden border-t border-border px-3 py-4 lg:block">
          <div className="flex items-center gap-3 rounded-lg px-3 py-2">
            {user.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt=""
                aria-hidden
                className="h-9 w-9 rounded-full border border-border object-cover"
              />
            ) : (
              <div
                aria-hidden
                className="flex h-9 w-9 items-center justify-center rounded-full bg-surface font-sans text-button font-bold text-fg-strong"
              >
                {user.githubUsername.charAt(0).toUpperCase() || '?'}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate text-button font-semibold text-fg-strong">
                @{user.githubUsername}
              </p>
              <p className="truncate text-caption text-fg-subtle">
                {user.email ?? 'GitHub 연결됨'}
              </p>
            </div>
            <ColorModeToggle className="-mr-1 shrink-0" />
          </div>
          <button
            type="button"
            onClick={logout}
            disabled={loggingOut}
            aria-busy={loggingOut}
            className="mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-button font-medium text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong disabled:cursor-not-allowed disabled:opacity-60"
          >
            <span aria-hidden className="shrink-0">
              <LogoutIcon />
            </span>
            <span>{loggingOut ? '로그아웃 중…' : '로그아웃'}</span>
          </button>
        </div>
      ) : null}
    </aside>
  )
}

function HomeIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3 8.5 10 3l7 5.5V16a1.5 1.5 0 0 1-1.5 1.5h-3v-5h-5v5h-3A1.5 1.5 0 0 1 3 16z" />
    </svg>
  )
}

function ResumeIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 2.5h6l4 4V16a1.5 1.5 0 0 1-1.5 1.5h-8A1.5 1.5 0 0 1 4 16V4A1.5 1.5 0 0 1 5 2.5Z" />
      <path d="M11 2.5V6a1 1 0 0 0 1 1h3" />
      <path d="M7 10.5h6M7 13.5h4" />
    </svg>
  )
}

function CoverLetterIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3.5 5.5A1.5 1.5 0 0 1 5 4h10a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 15 16H5a1.5 1.5 0 0 1-1.5-1.5Z" />
      <path d="M6.5 8h7M6.5 11h7M6.5 13.5h4" />
    </svg>
  )
}

function RepoIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="6" cy="5" r="2" />
      <circle cx="6" cy="15" r="2" />
      <circle cx="14" cy="7.5" r="2" />
      <path d="M6 7v6M6 13a4 4 0 0 0 4-4h2.2" />
    </svg>
  )
}

function HistoryIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="10" cy="10" r="7.5" />
      <path d="M10 5.5V10l3 2" />
    </svg>
  )
}

function LogoutIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M7.5 6.5V4.5a1.5 1.5 0 0 1 1.5-1.5h6A1.5 1.5 0 0 1 16.5 4.5v11a1.5 1.5 0 0 1-1.5 1.5H9a1.5 1.5 0 0 1-1.5-1.5v-2" />
      <path d="M11 10H3m0 0 2.5-2.5M3 10l2.5 2.5" />
    </svg>
  )
}
