import { useParams } from 'react-router-dom'
import { LiveInterview } from '@/features/interview'

export default function InterviewSessionPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const valid = Number.isFinite(sessionId) && sessionId > 0

  if (!valid) {
    return (
      <div className="flex min-h-svh items-center justify-center bg-bg text-fg">
        <p className="text-center text-fg-muted">잘못된 세션입니다.</p>
      </div>
    )
  }

  // 라이브 면접은 전역 헤더·푸터 없이 뷰포트 전체를 차지하는 몰입형 화면.
  return (
    <div className="h-svh w-full overflow-hidden bg-bg text-fg">
      <LiveInterview sessionId={sessionId} />
    </div>
  )
}
