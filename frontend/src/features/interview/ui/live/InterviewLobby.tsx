import { Button } from '@/shared/ui/Button'
import { sessionProgress } from '@/domain/session'
import type { Session } from '@/domain/session'
import { useSessionLifecycle } from '../../model/useSessionLifecycle'

export function InterviewLobby({ sessionId, session }: { sessionId: number; session: Session }) {
  const { start } = useSessionLifecycle(sessionId)
  const progress = sessionProgress(session)
  return (
    <div className="mx-auto flex h-full max-w-readable flex-col items-center justify-center gap-6 px-4 text-center">
      <h1 className="text-h4 text-fg">{session.title ?? '모의 면접'}</h1>
      <p className="text-body text-fg-muted">
        총 {progress.max}개의 질문이 준비됩니다. 시작하면 첫 질문이 곧 도착합니다.
      </p>
      <Button size="lg" loading={start.isPending} onClick={() => start.mutate()}>
        면접 시작
      </Button>
    </div>
  )
}
