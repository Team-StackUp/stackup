import { Link, useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { Button } from '@/shared/ui/Button'
import {
  FeedbackReport,
  isFeedbackPending,
  useFeedback,
  useRegenerateFeedback,
} from '@/features/feedback'
import { InterviewTranscript } from '@/features/interview'

export default function SessionFeedbackPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const { data, isLoading, isError, error, refetch } = useFeedback(sessionId)
  const regenerate = useRegenerateFeedback(sessionId)

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

        {isError &&
          (isFeedbackPending(error) ? (
            // polling 40회(약 2분)를 다 써도 피드백이 없으면 생성 요청이 유실됐을 가능성이
            // 높다(브로커 다운·AI 실패). 무한 대기 대신 재생성 복구 경로를 연다.
            <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
              <p className="text-body text-fg">피드백 생성이 예상보다 오래 걸리고 있어요.</p>
              <p className="text-caption text-fg-muted">
                생성 요청이 유실됐을 수 있습니다. 다시 생성을 요청해 보세요.
              </p>
              <Button
                onClick={() => regenerate.mutate()}
                disabled={regenerate.isPending}
              >
                {regenerate.isPending ? '요청 중…' : '피드백 다시 생성'}
              </Button>
            </div>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
              <p className="text-body text-fg">피드백을 불러오지 못했습니다.</p>
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            </div>
          ))}

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
