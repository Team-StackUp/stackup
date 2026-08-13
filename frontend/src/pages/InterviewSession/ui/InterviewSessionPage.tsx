import { useParams } from 'react-router-dom'
import { LiveInterview } from '@/features/interview'

export default function InterviewSessionPage() {
  const { id } = useParams<{ id: string }>()
  const sessionId = Number(id)
  const valid = Number.isFinite(sessionId) && sessionId > 0

  if (!valid) {
    return (
      <div className="flex min-h-svh items-center justify-center bg-surface-raised text-fg">
        <p className="text-center text-fg-muted">잘못된 세션입니다.</p>
      </div>
    )
  }

  // 라이브 면접은 전역 헤더·푸터 없이 뷰포트 전체를 차지하는 몰입형 화면.
  //
  // 배경은 `bg-bg`(basement) 가 아니라 다른 화면과 같은 `surface-raised` 를 쓴다 —
  // 다크에서 basement 는 거의 순흑이라, 배경 사진이 깔리는 스테이지가 아닌 상태
  // (로비·첫질문 대기·종료·에러)에서 텍스트만 검은 허공에 뜬 화면이 됐다.
  return (
    <div className="h-svh w-full overflow-hidden bg-surface-raised text-fg">
      <LiveInterview sessionId={sessionId} />
    </div>
  )
}
