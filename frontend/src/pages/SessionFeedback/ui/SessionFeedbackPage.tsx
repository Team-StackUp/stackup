import { Link, useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { Button } from '@/shared/ui/Button'

// 피드백 리포트 화면(features/feedback)은 아직 범위 밖. feedback.ready 리다이렉트가
// 빈 화면으로 끝나지 않도록 임시 안내 스텁을 제공한다.
export default function SessionFeedbackPage() {
  const { id } = useParams<{ id: string }>()
  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto flex w-full max-w-content flex-1 flex-col items-center justify-center gap-4 px-6 py-16 text-center lg:px-12">
        <h1 className="text-h4 text-fg">피드백 준비 완료</h1>
        <p className="text-body text-fg-muted">
          세션 #{id}의 면접이 종료되어 피드백이 준비되었습니다. 상세 리포트 화면은 준비 중입니다.
        </p>
        <Link to="/workspace">
          <Button variant="secondary">워크스페이스로</Button>
        </Link>
      </main>
      <SiteFooter />
    </div>
  )
}
