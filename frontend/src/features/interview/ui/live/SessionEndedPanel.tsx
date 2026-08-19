import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { Eyebrow } from '@/shared/ui'
import type { Session, SessionStatus } from '@/domain/session'
import { useResumeSession } from '../../model/useResumeSession'
import { useRetrySession } from '../../model/useRetrySession'
import { InterviewTranscript } from '../InterviewTranscript'

const messageByStatus: Partial<Record<SessionStatus, string>> = {
  COMPLETED: '면접이 종료되었습니다. 피드백을 준비 중입니다.',
  INTERRUPTED: '면접이 중단되었습니다.',
  CANCELLED: '면접이 취소되었습니다.',
}

// 중단된 면접은 피드백이 만들어지지 않는다. 그렇다고 주고받은 문답까지 못 보게 하면
// 사용자가 한 말이 통째로 사라진 것처럼 보이므로, 여기서 기록을 그대로 노출한다.
// (취소된 세션은 시작 전이라 자기소개 질문 하나뿐 — 보여줄 게 없다.)
const showsTranscript = (status: SessionStatus) =>
  status === 'COMPLETED' || status === 'INTERRUPTED'

export function SessionEndedPanel({
  status,
  sessionId,
  session,
}: {
  status: SessionStatus
  sessionId: number
  session?: Session
}) {
  const retry = useRetrySession(session?.contextDocumentIds?.length)
  const resume = useResumeSession(sessionId)

  return (
    <div className="flex h-full flex-col overflow-y-auto">
      <div className="flex flex-col items-center justify-center gap-4 px-6 py-16 text-center">
        <Eyebrow>면접 종료</Eyebrow>
        <p
          className="font-sans text-[22px] font-bold tracking-[-0.03em] text-fg"
          style={{ wordBreak: 'keep-all' }}
        >
          {messageByStatus[status] ?? '면접이 종료되었습니다.'}
        </p>
        <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
          {/* 중단된 면접은 이어서 하는 게 첫 선택지다 — 하던 대화가 그대로 남아 있다. */}
          {status === 'INTERRUPTED' && (
            <Button loading={resume.isPending} onClick={() => resume.mutate()}>
              이어서 진행하기
            </Button>
          )}
          {status === 'COMPLETED' && (
            <Link to={`/sessions/${sessionId}/feedback`}>
              <Button>피드백 보기</Button>
            </Link>
          )}
          {/* 중단됐든 끝났든, 같은 조건으로 한 번 더 해보는 게 다음 행동이다. */}
          <Button
            variant="secondary"
            loading={retry.isPending}
            onClick={() => retry.mutate(sessionId)}
          >
            같은 설정으로 다시
          </Button>
          {/* 약점 집중은 피드백 점수가 근거라 완료 세션에서만 뜻이 있다. */}
          {status === 'COMPLETED' && (
            <Button
              loading={retry.isPending}
              onClick={() => retry.mutate({ sessionId, focusOnWeakness: true })}
            >
              약점 집중해서 다시
            </Button>
          )}
          <Link to="/workspace">
            <Button variant="secondary">워크스페이스로</Button>
          </Link>
        </div>
      </div>

      {showsTranscript(status) && (
        <div className="mx-auto w-full max-w-3xl px-6 pb-16">
          <InterviewTranscript sessionId={sessionId} />
        </div>
      )}
    </div>
  )
}
