import { Link, useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { Button } from '@/shared/ui/Button'
import { FeedbackReport, useFeedback } from '@/features/feedback'
import { InterviewTranscript } from '@/features/interview'

export default function SessionFeedbackPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const { data, isLoading, isError, refetch } = useFeedback(sessionId)

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col gap-8 px-6 py-12 lg:px-12">
        <header className="flex items-center justify-between gap-4">
          <h1 className="text-h4 text-fg">면접 피드백</h1>
          <Link to="/workspace">
            <Button variant="secondary">워크스페이스로</Button>
          </Link>
        </header>

        {isLoading && (
          <div className="flex flex-1 flex-col items-center justify-center gap-2 py-16 text-center">
            <p className="text-body text-fg">피드백을 생성하는 중입니다…</p>
            <p className="text-caption text-fg-muted">
              답변을 종합 분석하고 있어요. 최대 1분가량 걸릴 수 있습니다.
            </p>
          </div>
        )}

        {isError && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
            <p className="text-body text-fg">피드백을 불러오지 못했습니다.</p>
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          </div>
        )}

        {data && (
          <>
            <FeedbackReport feedback={data} shareable />
            <InterviewTranscript sessionId={sessionId} />
          </>
        )}
      </main>
      <SiteFooter />
    </div>
  )
}
