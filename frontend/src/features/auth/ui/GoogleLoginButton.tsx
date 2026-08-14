import { useState } from 'react'
import { startGoogleLogin } from '../api/auth'

type GoogleLoginButtonProps = {
  className?: string
  label?: string
  onError?: (error: unknown) => void
}

// GitHub 버튼과 같은 형태(h-12 · rounded-xl)로 맞춰 두 버튼이 한 덩어리로 읽히게 한다.
// 색만 반대 — Google 브랜드 가이드가 흰(또는 중립) 바탕에 컬러 G 마크를 요구하고,
// 우리 디자인 시스템의 헤어라인 표면과도 그대로 맞는다.
const BASE_CLASS =
  'inline-flex items-center justify-center gap-3 h-12 px-6 rounded-xl border border-border-strong bg-surface-raised text-button font-semibold text-fg-strong transition-colors duration-fast hover:bg-surface disabled:cursor-not-allowed disabled:opacity-60'

export function GoogleLoginButton({
  className,
  label = 'Google로 계속하기',
  onError,
}: GoogleLoginButtonProps) {
  const [loading, setLoading] = useState(false)

  const handleClick = async () => {
    if (loading) return
    setLoading(true)
    try {
      const { authorizationUrl } = await startGoogleLogin()
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
      {loading ? <Spinner /> : <GoogleMark />}
      <span>{loading ? '이동 중…' : label}</span>
    </button>
  )
}

// Google 공식 G 마크. 브랜드 색이 고정이라 토큰을 쓰지 않는다(다크에서도 동일해야 한다).
function GoogleMark() {
  return (
    <svg aria-hidden width="18" height="18" viewBox="0 0 48 48">
      <path
        fill="#EA4335"
        d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      />
      <path
        fill="#4285F4"
        d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      />
      <path
        fill="#FBBC05"
        d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      />
      <path
        fill="#34A853"
        d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      />
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
