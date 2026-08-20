import { useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { FeedbackReport, useSharedFeedback } from '@/features/feedback'
import { PageHeader } from '@/shared/ui'
import { useNoIndex } from '@/shared/hooks'

// 공유 토큰으로 피드백을 보는 공개(비로그인) 페이지.
export default function SharedFeedbackPage() {
  // 링크를 받은 사람만 보라고 만든 페이지다 — 검색 결과에 뜨면 안 된다.
  useNoIndex()
  const { token } = useParams<{ token: string }>()
  const { data, isLoading, isError } = useSharedFeedback(token ?? '')

  return (
    <div className="flex min-h-svh flex-col bg-surface-raised text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col gap-10 px-6 py-12 lg:px-12 lg:py-16">
        <PageHeader
          eyebrow="공유 리포트"
          title="공유된 면접 피드백"
          description="링크를 받은 사람이 볼 수 있는 읽기 전용 리포트입니다."
        />

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
