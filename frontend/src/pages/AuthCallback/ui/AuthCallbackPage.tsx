import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { completeGithubLogin, completeGoogleLogin, useAuth } from '@/features/auth'
import { consumeReturnTo } from '@/features/auth/lib/return-to'
import { isApiError } from '@/shared/api'
import { Eyebrow, Heading } from '@/shared/ui'

const consumedCodes = new Set<string>()

export default function AuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { applyLogin } = useAuth()

  // 두 provider 가 같은 페이지를 쓰고 경로로만 갈린다 — 콜백 화면은 문구까지 동일해서
  // 페이지를 나누면 같은 코드가 두 벌이 된다.
  const isGoogle = pathname.startsWith('/auth/google')
  const providerLabel = isGoogle ? 'Google' : 'GitHub'

  const code = params.get('code')
  const stateParam = params.get('state')
  const missingParams = !code || !stateParam

  const [error, setError] = useState<string | null>(
    missingParams ? `${providerLabel} 응답에 code 또는 state가 없습니다.` : null,
  )

  useEffect(() => {
    if (missingParams) return
    const key = `${code}:${stateParam}`
    if (consumedCodes.has(key)) return
    consumedCodes.add(key)

    void (async () => {
      try {
        const response = isGoogle
          ? await completeGoogleLogin(code!, stateParam!)
          : await completeGithubLogin(code!, stateParam!)
        applyLogin(response)
        const dest = consumeReturnTo() ?? '/'
        navigate(dest, { replace: true })
      } catch (err) {
        if (isApiError(err)) {
          setError(err.message)
        } else {
          setError('로그인 처리 중 오류가 발생했습니다.')
        }
      }
    })()
  }, [missingParams, code, stateParam, applyLogin, navigate, isGoogle])

  return (
    <div className="flex min-h-svh items-center justify-center bg-surface-raised px-6 py-16 text-fg">
      <div className="w-full max-w-md text-center">
        {error ? (
          <>
            <p className="font-mono text-caption tracking-tight text-danger-700">로그인 실패</p>
            <Heading level="section" as="h1" className="mt-3">
              로그인을 완료하지 못했어요
            </Heading>
            <p
              className="mt-3 text-body font-normal leading-relaxed text-fg-muted"
              style={{ wordBreak: 'keep-all' }}
            >
              {error}
            </p>
            <Link
              to="/login"
              replace
              className="mt-8 inline-flex items-center justify-center rounded-xl bg-primary px-6 py-3.5 text-body font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
            >
              로그인 페이지로 돌아가기
            </Link>
          </>
        ) : (
          <>
            <Spinner />
            <Eyebrow className="mt-6">인증 중</Eyebrow>
            <Heading level="section" as="h1" className="mt-2.5">
              {providerLabel} 로그인 처리 중…
            </Heading>
            <p className="mt-3 text-body font-normal text-fg-muted">
              잠시만 기다려주세요. 곧 이동합니다.
            </p>
          </>
        )}
      </div>
    </div>
  )
}

function Spinner() {
  return (
    <svg
      aria-hidden
      width="40"
      height="40"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.25"
      className="animate-spin mx-auto text-fg-muted"
    >
      <circle cx="12" cy="12" r="9" strokeOpacity="0.2" />
      <path d="M21 12a9 9 0 0 0-9-9" strokeLinecap="round" />
    </svg>
  )
}
