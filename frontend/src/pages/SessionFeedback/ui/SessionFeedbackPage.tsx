import { Link, useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { Button } from '@/shared/ui/Button'
import { PageHeader } from '@/shared/ui'
import {
  FeedbackReport,
  isFeedbackPending,
  useFeedback,
  useRegenerateFeedback,
} from '@/features/feedback'
import { InterviewTranscript, useRetrySession, useSession } from '@/features/interview'

export default function SessionFeedbackPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const { data, isLoading, isError, error, refetch } = useFeedback(sessionId)
  const regenerate = useRegenerateFeedback(sessionId)
  // 재도전은 원본 세션의 자료 수를 알아야 "몇 개가 빠졌는지" 안내할 수 있다.
  const { data: session } = useSession(sessionId)
  const retry = useRetrySession(session?.contextDocumentIds?.length)

  return (
    <div className="flex min-h-svh flex-col bg-surface-raised text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col gap-10 px-6 py-12 lg:px-12 lg:py-16">
        <PageHeader
          eyebrow="리포트"
          title="면접 피드백"
          description="점수 옆에 그렇게 매긴 근거가 함께 붙습니다."
          actions={
            <div className="flex flex-wrap items-center gap-2">
              {/* 점수가 나온 뒤라, 여기서는 약점 집중이 기본 제안이다. */}
              <Button
                loading={retry.isPending}
                onClick={() => retry.mutate({ sessionId, focusOnWeakness: true })}
              >
                약점 집중해서 다시
              </Button>
              <Button
                variant="secondary"
                loading={retry.isPending}
                onClick={() => retry.mutate(sessionId)}
              >
                같은 설정으로 다시
              </Button>
              <Link to="/workspace">
                <Button variant="secondary">워크스페이스로</Button>
              </Link>
            </div>
          }
        />

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
