import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { GithubLoginButton, GoogleLoginButton, useAuth } from '@/features/auth'
import { rememberReturnTo } from '@/features/auth/lib/return-to'
import { isApiError } from '@/shared/api'
import { ColorModeToggle, Eyebrow, Heading, Panel, Reveal } from '@/shared/ui'
import { SiteFooter } from '@/widgets/site-footer'

type LocationState = { returnTo?: string } | null

// 로그인 뒤 무엇이 준비되는지 — 랜딩 히어로의 숫자 지표와 같은 조판으로 보여준다.
const facts = [
  { v: '01', label: '레포 분석' },
  { v: '02', label: 'AI 면접' },
  { v: '03', label: '피드백' },
]

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

  const handleLoginError = (err: unknown) => {
    setError(
      isApiError(err)
        ? err.message
        : '로그인을 시작할 수 없습니다. 잠시 후 다시 시도해주세요.',
    )
  }

  return (
    <div className="flex min-h-svh flex-col bg-surface-raised text-fg">
      {/*
        SiteNav 를 그대로 쓰지 않는 이유 — 로그인 화면에서 '시작하기'/'로그인' CTA 는
        자기 자신을 가리킨다. 대신 SiteNav 와 같은 형태(h-16 · 헤어라인 · 같은 로고
        조판 · 모드 토글)만 맞춘 얇은 헤더를 둔다.
      */}
      <header className="sticky top-0 border-b border-border bg-surface-raised/85 backdrop-blur-md" style={{ zIndex: 'var(--z-sticky)' }}>
        <div className="mx-auto flex h-16 max-w-content items-center justify-between px-6 lg:px-12">
          <Link to="/#top" className="font-sans text-[17px] font-bold tracking-tight text-fg">
            STACK-UP
          </Link>
          <div className="flex items-center gap-1.5">
            <ColorModeToggle />
            <Link
              to="/"
              className="rounded-md px-3 py-2 text-button text-fg-muted transition-colors duration-fast hover:text-fg-strong"
            >
              홈으로
            </Link>
          </div>
        </div>
      </header>

      <main className="flex flex-1 items-center px-6 py-16 lg:px-12">
        <div className="mx-auto w-full max-w-md">
          <Reveal>
            <Eyebrow>로그인</Eyebrow>
            <Heading level="page" as="h1" className="mt-3">
              모의 면접을 시작해볼까요?
            </Heading>
            <p
              className="mt-4 text-body font-normal text-fg-muted"
              style={{ wordBreak: 'keep-all' }}
            >
              로그인하면 내 이력서·자소서·레포지토리를 읽은 면접관이 준비됩니다.
            </p>
          </Reveal>

          <Reveal delayMs={60}>
            <Panel padding="lg" className="mt-8">
              <div className="flex flex-col gap-2.5">
                <GithubLoginButton className="w-full" onError={handleLoginError} />
                <GoogleLoginButton className="w-full" onError={handleLoginError} />
              </div>

              {/*
                두 방식의 차이를 로그인 전에 알린다 — Google 로 가입한 뒤 레포 화면에서
                비로소 막히는 것보다, 고르는 시점에 아는 편이 낫다.
              */}
              <p
                className="mt-3.5 text-center text-caption leading-relaxed text-fg-subtle"
                style={{ wordBreak: 'keep-all' }}
              >
                레포지토리 기반 질문은 GitHub 로그인에서만 제공됩니다. Google 계정은 이력서·자소서로
                면접을 볼 수 있어요.
              </p>

              {error ? (
                <div
                  role="alert"
                  className="mt-4 rounded-lg border border-danger-500/30 bg-danger-50 px-3 py-2 text-caption text-danger-700"
                >
                  {error}
                </div>
              ) : null}

              <p className="mt-6 text-center text-caption leading-relaxed text-fg-muted">
                계속 진행하면 STACK-UP의{' '}
                <a href="#" className="underline underline-offset-2 hover:text-fg-strong">
                  이용약관
                </a>
                과{' '}
                <a href="#" className="underline underline-offset-2 hover:text-fg-strong">
                  개인정보 처리방침
                </a>
                에 동의하는 것으로 간주됩니다.
              </p>
            </Panel>
          </Reveal>

          <Reveal delayMs={120}>
            <dl className="mt-10 flex divide-x divide-border border-t border-border pt-6">
              {facts.map((f) => (
                <div key={f.label} className="flex-1 pr-4 first:pl-0 [&:not(:first-child)]:pl-5">
                  <dd
                    className="font-mono font-semibold leading-none text-fg-faint"
                    style={{ fontSize: '22px', letterSpacing: '-0.04em' }}
                  >
                    {f.v}
                  </dd>
                  <dt className="mt-1.5 text-caption text-fg-subtle">{f.label}</dt>
                </div>
              ))}
            </dl>
          </Reveal>
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}
