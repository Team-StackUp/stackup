import { useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { FeedbackReport, useSharedFeedback } from '@/features/feedback'

// 공유 토큰으로 피드백을 보는 공개(비로그인) 페이지.
export default function SharedFeedbackPage() {
  const { token } = useParams<{ token: string }>()
  const { data, isLoading, isError } = useSharedFeedback(token ?? '')

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col gap-8 px-6 py-12 lg:px-12">
        <h1 className="text-h4 text-fg">공유된 면접 피드백</h1>

        {isLoading && (
          <p className="py-16 text-center text-body text-fg-muted">불러오는 중…</p>
        )}

        {isError && (
          <p className="py-16 text-center text-body text-fg-muted">
            유효하지 않거나 만료된 공유 링크입니다.
          </p>
        )}

        {data && <FeedbackReport feedback={data} />}
      </main>
      <SiteFooter cta />
    </div>
  )
}
