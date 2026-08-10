import { Link, useRouteError } from 'react-router-dom'

/**
 * 라우트 레벨 오류 화면 — 렌더 중 throw(예: 서버가 새 enum 을 내려 STATUS_META[x] 접근이
 * 터지는 경우)가 발생하면 react-router 의 기본 개발자용 에러 화면 대신 이걸 보여준다.
 * 없으면 사용자는 완전 백지(또는 영어 스택트레이스)를 만난다.
 */
export function RouteError() {
  const error = useRouteError()
  // 화면에는 원문을 노출하지 않고 콘솔에만 남긴다 — 사용자에게 스택트레이스는 소음이다.
  console.error('[route-error]', error)
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-bg px-6 text-center text-fg">
      <h1
        className="font-sans font-bold text-fg"
        style={{ fontSize: 'clamp(24px, 3vw, 34px)', letterSpacing: '-0.03em' }}
      >
        문제가 발생했습니다
      </h1>
      <p className="text-body font-normal text-fg-muted" style={{ wordBreak: 'keep-all' }}>
        일시적인 오류일 수 있어요. 새로고침하거나 홈으로 이동해 주세요.
      </p>
      <div className="mt-2 flex gap-2">
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="rounded-lg border border-border-strong px-4 py-2 text-button text-fg-strong transition-colors duration-fast hover:bg-surface"
        >
          새로고침
        </button>
        <Link
          to="/"
          className="rounded-lg bg-primary px-4 py-2 text-button text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
        >
          홈으로
        </Link>
      </div>
    </div>
  )
}
