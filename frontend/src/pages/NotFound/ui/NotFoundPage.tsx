import { Link } from 'react-router-dom'
import { Eyebrow, Heading } from '@/shared/ui'

export default function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-surface-raised px-6 text-center text-fg">
      <Eyebrow>404</Eyebrow>
      <Heading level="section" as="h1">
        페이지를 찾을 수 없습니다
      </Heading>
      <p className="text-body font-normal text-fg-muted" style={{ wordBreak: 'keep-all' }}>
        주소가 바뀌었거나 삭제된 페이지예요.
      </p>
      <Link
        to="/"
        className="mt-2 rounded-xl bg-primary px-6 py-3.5 text-body font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
      >
        홈으로
      </Link>
    </div>
  )
}
