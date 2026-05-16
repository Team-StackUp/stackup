import { useState } from 'react'
import { startGithubLogin } from '../api/auth'

type GithubLoginButtonProps = {
  className?: string
  label?: string
  onError?: (error: unknown) => void
}

const BASE_CLASS =
  'inline-flex items-center justify-center gap-3 h-12 px-6 rounded-xl bg-sage-900 hover:bg-sage-800 active:bg-sage-700 text-white text-button transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed'

export function GithubLoginButton({
  className,
  label = 'GitHub으로 계속하기',
  onError,
}: GithubLoginButtonProps) {
  const [loading, setLoading] = useState(false)

  const handleClick = async () => {
    if (loading) return
    setLoading(true)
    try {
      const { authorizationUrl } = await startGithubLogin()
      window.location.href = authorizationUrl
    } catch (error) {
      setLoading(false)
      onError?.(error)
    }
  }

  return (
    <button
      type="button"
      className={[BASE_CLASS, className].filter(Boolean).join(' ')}
      onClick={handleClick}
      disabled={loading}
      aria-busy={loading}
    >
      {loading ? <Spinner /> : <GithubMark />}
      <span>{loading ? '이동 중…' : label}</span>
    </button>
  )
}

function GithubMark() {
  return (
    <svg
      aria-hidden
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="currentColor"
    >
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.111.82-.261.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.305-5.467-1.334-5.467-5.93 0-1.31.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.553 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.371.823 1.102.823 2.222 0 1.606-.014 2.898-.014 3.293 0 .319.218.694.825.576C20.565 22.092 24 17.594 24 12.297c0-6.627-5.373-12-12-12" />
    </svg>
  )
}

function Spinner() {
  return (
    <svg
      aria-hidden
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      className="animate-spin"
    >
      <circle cx="12" cy="12" r="9" strokeOpacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" strokeLinecap="round" />
    </svg>
  )
}
