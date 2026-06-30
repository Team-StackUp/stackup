import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { GithubLoginButton, useAuth } from '@/features/auth'
import { rememberReturnTo } from '@/features/auth/lib/return-to'
import { isApiError } from '@/shared/api'

type LocationState = { returnTo?: string } | null

export default function LoginPage() {
  const [error, setError] = useState<string | null>(null)
  const location = useLocation()
  const { status } = useAuth()

  const returnTo = (location.state as LocationState)?.returnTo ?? null

  useEffect(() => {
    rememberReturnTo(returnTo)
  }, [returnTo])

  if (status === 'authenticated') {
    return <Navigate to={returnTo ?? '/'} replace />
  }

  return (
    <div className="min-h-svh bg-bg text-fg flex flex-col">
      <header className="sticky top-0 z-10 bg-bg/85 backdrop-blur-md border-b border-border/60">
        <div className="mx-auto max-w-content px-6 lg:px-12 h-16 flex items-center justify-between">
          <Link
            to="/"
            className="font-heading font-extrabold tracking-[0.04em] text-sage-900 text-[15px] uppercase"
          >
            Stack Up
          </Link>
          <Link
            to="/"
            className="text-button text-fg-strong/80 hover:text-fg-strong transition-colors duration-fast"
          >
            ← 홈으로
          </Link>
        </div>
      </header>

      <main className="flex-1 flex items-center justify-center px-6 py-16">
        <div className="w-full max-w-md">
          <div className="text-center">
            <p className="text-caption font-mono uppercase tracking-[0.22em] text-sage-500">
              Sign in
            </p>
            <h1
              className="mt-4 font-heading font-extrabold text-sage-900 leading-[1.05]"
              style={{ fontSize: 'clamp(36px, 5vw, 52px)', letterSpacing: '-0.5px' }}
            >
              모의 면접을<br />시작해볼까요?
            </h1>
            <p className="mt-5 text-fg-strong/80 leading-relaxed">
              GitHub 계정으로 로그인하면<br />
              당신의 레포지토리 기반 맞춤 면접이 준비됩니다.
            </p>
          </div>

          <div className="mt-10 bg-surface-raised rounded-2xl border border-border shadow-md p-8">
            <GithubLoginButton
              className="w-full"
              onError={(err) => {
                if (isApiError(err)) {
                  setError(err.message)
                } else {
                  setError('로그인을 시작할 수 없습니다. 잠시 후 다시 시도해주세요.')
                }
              }}
            />

            {error ? (
              <div
                role="alert"
                className="mt-4 rounded-md border border-danger-500/30 bg-danger-50 px-3 py-2 text-caption text-danger-700"
              >
                {error}
              </div>
            ) : null}

            <p className="mt-6 text-caption text-fg-muted text-center leading-relaxed">
              계속 진행하면 StackUp의{' '}
              <a href="#" className="underline hover:text-fg-strong">이용약관</a>과{' '}
              <a href="#" className="underline hover:text-fg-strong">개인정보 처리방침</a>에
              동의하는 것으로 간주됩니다.
            </p>
          </div>

          <ul className="mt-10 grid grid-cols-3 gap-4 text-center">
            {features.map((f) => (
              <li key={f.title}>
                <div className="text-h6 font-heading font-extrabold text-sage-700">
                  {f.icon}
                </div>
                <div className="mt-2 text-caption font-mono uppercase tracking-[0.18em] text-sage-500">
                  {f.title}
                </div>
              </li>
            ))}
          </ul>
        </div>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto max-w-content px-6 lg:px-12 h-14 flex items-center justify-between text-caption font-mono text-fg-muted">
          <span>© 2026 StackUp</span>
          <Link to="/" className="hover:text-fg-strong transition-colors duration-fast">
            ← Back to home
          </Link>
        </div>
      </footer>
    </div>
  )
}

const features = [
  { icon: '01', title: 'Repo 분석' },
  { icon: '02', title: 'AI 면접' },
  { icon: '03', title: '피드백' },
]
