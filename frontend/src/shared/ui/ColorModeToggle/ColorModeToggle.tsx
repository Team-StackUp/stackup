import { useColorMode } from '@/shared/lib/color-mode'

/**
 * 라이트/다크 전환 버튼.
 *
 * `SiteNav` 안에만 있던 것을 공용으로 끌어올렸다 — 로그인처럼 SiteNav 를 쓰지 않는
 * 화면에서도 모드 전환이 사라지지 않아야 한다(전환 수단이 화면마다 있다 없다 하면
 * 다크 모드가 "일부 화면만 되는 기능"으로 읽힌다).
 */
export function ColorModeToggle({ className = '' }: { className?: string }) {
  const { isDark, toggle } = useColorMode()
  const label = isDark ? '라이트 모드로 전환' : '다크 모드로 전환'

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className={`inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong ${className}`}
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
          <path d="M20 14.2A8.2 8.2 0 0 1 9.8 4a8.5 8.5 0 1 0 10.2 10.2Z" fill="currentColor" />
        </svg>
      )}
    </button>
  )
}
