import { useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { LiveInterview } from '@/features/interview'

export default function InterviewSessionPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const valid = Number.isFinite(sessionId) && sessionId > 0

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto w-full max-w-content flex-1 px-6 py-6 lg:px-12">
        {valid ? (
          // 라이브 면접은 몰입형 스테이지 — 배경 위에 현재 질문 집중 + composer 하단 고정.
          <div className="flex h-[78svh] min-h-120 flex-col overflow-hidden rounded-xl border border-border bg-surface-raised shadow-sm">
            <LiveInterview sessionId={sessionId} />
          </div>
        ) : (
          <p className="py-16 text-center text-fg-muted">잘못된 세션입니다.</p>
        )}
      </main>
      <SiteFooter />
    </div>
  )
}
