import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-bg px-6 text-center text-fg">
      <p className="font-mono text-caption tracking-tight text-fg-subtle">404</p>
      <h1
        className="font-sans font-bold text-fg"
        style={{ fontSize: 'clamp(24px, 3vw, 34px)', letterSpacing: '-0.03em' }}
      >
        페이지를 찾을 수 없습니다
      </h1>
      <p className="text-body font-normal text-fg-muted" style={{ wordBreak: 'keep-all' }}>
        주소가 바뀌었거나 삭제된 페이지예요.
      </p>
      <Link
        to="/"
        className="mt-2 rounded-lg bg-primary px-4 py-2 text-button text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
      >
        홈으로
      </Link>
    </div>
  )
}
