import { sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'
import { Heading } from '@/shared/ui'

// 면접 시작 직후, 첫 질문이 준비될 때까지 스테이지 진입 전에 머무는 대기 화면.
// 첫 질문이 도착하면 LiveInterview 가 InterviewStage 로 전환하며 화면이 "켜진다".
export function InterviewPreparing({
  session,
  progressMessage,
}: {
  session: Session
  /** AI 질문 풀 생성 진행 문구(QUESTION_POOL_PROGRESS). 없으면 기본 안내 문구. */
  progressMessage?: string | null
}) {
  const progress = sessionProgress(session)
  return (
    <div className="mx-auto flex h-full max-w-readable flex-col items-center justify-center gap-6 px-4 text-center">
      <span className="flex gap-2" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-3 w-3 animate-pulse rounded-full bg-fg-subtle"
            style={{ animationDelay: `${i * 180}ms` }}
          />
        ))}
      </span>
      <Heading level="page" as="h1">
        {session.title ?? '모의 면접'}
      </Heading>
      <p className="text-body font-normal text-fg-muted" role="status" style={{ wordBreak: 'keep-all' }}>
        {progressMessage ?? '첫 질문을 만들고 있어요. 준비가 끝나면 바로 면접이 시작됩니다.'}
      </p>
      <p className="text-caption text-fg-subtle">총 {progress.max}개의 질문이 준비됩니다.</p>
    </div>
  )
}
