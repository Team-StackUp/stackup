import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { Eyebrow } from '@/shared/ui'
import type { SessionStatus } from '@/domain/session'

const messageByStatus: Partial<Record<SessionStatus, string>> = {
  COMPLETED: '면접이 종료되었습니다. 피드백을 준비 중입니다.',
  INTERRUPTED: '면접이 중단되었습니다.',
  CANCELLED: '면접이 취소되었습니다.',
}

export function SessionEndedPanel({
  status,
  sessionId,
}: {
  status: SessionStatus
  sessionId: number
}) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 px-6 text-center">
      <Eyebrow>면접 종료</Eyebrow>
      <p
        className="font-sans text-[22px] font-bold tracking-[-0.03em] text-fg"
        style={{ wordBreak: 'keep-all' }}
      >
        {messageByStatus[status] ?? '면접이 종료되었습니다.'}
      </p>
      <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
        {status === 'COMPLETED' && (
          <Link to={`/sessions/${sessionId}/feedback`}>
            <Button>피드백 보기</Button>
          </Link>
        )}
        <Link to="/workspace">
          <Button variant="secondary">워크스페이스로</Button>
        </Link>
      </div>
    </div>
  )
}
