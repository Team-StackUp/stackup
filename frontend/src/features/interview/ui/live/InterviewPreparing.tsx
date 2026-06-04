import { sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'

// 면접 시작 직후, 첫 질문이 준비될 때까지 스테이지 진입 전에 머무는 대기 화면.
// 첫 질문이 도착하면 LiveInterview 가 InterviewStage 로 전환하며 화면이 "켜진다".
export function InterviewPreparing({ session }: { session: Session }) {
  const progress = sessionProgress(session)
  return (
    <div className="mx-auto flex h-full max-w-readable flex-col items-center justify-center gap-6 px-4 text-center">
      <span className="flex gap-2" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-3 w-3 animate-pulse rounded-full bg-sage-700/70"
            style={{ animationDelay: `${i * 180}ms` }}
          />
        ))}
      </span>
      <h1 className="text-h4 text-fg">{session.title ?? '모의 면접'}</h1>
      <p className="text-body text-fg-muted" role="status">
        첫 질문을 만들고 있어요. 준비가 끝나면 바로 면접이 시작됩니다.
      </p>
      <p className="text-caption text-fg-subtle">총 {progress.max}개의 질문이 준비됩니다.</p>
    </div>
  )
}
