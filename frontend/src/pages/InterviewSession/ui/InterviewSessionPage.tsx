import { useParams } from 'react-router-dom'
import { LiveInterview } from '@/features/interview'

export default function InterviewSessionPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    return <div className="px-4 py-16 text-center text-fg-muted">잘못된 세션입니다.</div>
  }
  return (
    <div className="mx-auto flex h-screen max-w-content flex-col">
      <LiveInterview sessionId={sessionId} />
    </div>
  )
}
