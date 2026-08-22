import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { Button } from '@/shared/ui/Button'
import { PageHeader } from '@/shared/ui'
import {
  FeedbackReport,
  FeedbackReportSkeleton,
  isFeedbackPending,
  useFeedbackLive,
  useRegenerateFeedback,
} from '@/features/feedback'
import {
  InterviewTranscript,
  fetchSessionStreamToken,
  useRetrySession,
  useSession,
} from '@/features/interview'

export default function SessionFeedbackPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  // 피드백 조회 + FEEDBACK_READY SSE — 준비 완료 즉시 표시(폴링은 백스톱).
  // 세션 stream token 은 interview 슬라이스 소유라 페이지가 주입한다.
  const getToken = useCallback(() => fetchSessionStreamToken(sessionId), [sessionId])
  const { data, isLoading, isError, error, refetch, progress, failure, resetFailure } =
    useFeedbackLive(sessionId, getToken)
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

        {/* 생성 실패 SSE(ERROR, scope=FEEDBACK) — 폴링 예산 소진(≈2분)을 기다리지 않고
            즉시 복구 경로를 연다. 그 사이 데이터가 도착했으면 리포트가 우선. */}
        {!data && failure && (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
            <p className="text-body text-fg">{failure.message}</p>
            {/* 마커가 영속이라 버튼을 숨기면 이 세션의 피드백은 UI 로 영원히 도달 불가가 된다 —
                retriable=false 여도 LLM 생성은 비결정적이고 서버도 재생성을 수락하므로 버튼은 유지,
                문구만 기대치를 낮춘다. 실패 해제는 재생성 성공(resetQueries)이 담당. */}
            <p className="text-caption text-fg-muted">
              {failure.retriable === false
                ? '같은 오류가 반복될 수 있지만, 다시 생성을 시도해 볼 수 있어요.'
                : '피드백 생성이 실패해 대기를 중단했어요. 다시 생성을 요청해 보세요.'}
            </p>
            <Button
              onClick={() => regenerate.mutate(undefined, { onSuccess: () => resetFailure() })}
              disabled={regenerate.isPending}
            >
              {regenerate.isPending ? '요청 중…' : '피드백 다시 생성'}
            </Button>
          </div>
        )}

        {!failure && isLoading && <FeedbackReportSkeleton progress={progress} />}

        {!failure && isError &&
          (isFeedbackPending(error) ? (
            // 백스톱 재시도 예산(모드 무관 ≈2분, useFeedbackLive)을 다 써도 피드백이 없으면
            // 생성 요청이 유실됐을 가능성이 높다(브로커 다운·AI 실패). 무한 대기 대신 복구 경로를 연다.
            <div className="flex flex-1 flex-col items-center justify-center gap-3 py-16 text-center">
              <p className="text-body text-fg">피드백 생성이 예상보다 오래 걸리고 있어요.</p>
              <p className="text-caption text-fg-muted">
                생성 요청이 유실됐을 수 있습니다. 다시 생성을 요청해 보세요.
              </p>
              {/* 재생성 직후 뒤늦게 도착하는 SSE ERROR(이전 시도의 실패)가 남지 않도록
                  여기서도 성공 시 failure 를 함께 걷는다. */}
              <Button
                onClick={() => regenerate.mutate(undefined, { onSuccess: () => resetFailure() })}
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
