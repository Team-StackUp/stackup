import { BookmarkList } from '@/features/interview'
import { WorkspaceSection } from '@/widgets/workspace-section'

export function BookmarksView() {
  return (
    <WorkspaceSection
      eyebrow="오답노트"
      title="다시 볼 질문"
      description="끝난 면접의 질문·답변 기록에서 별을 누르면 여기에 모입니다. 모범 답안은 접혀 있으니 먼저 스스로 답해 보세요."
    >
      <BookmarkList />
    </WorkspaceSection>
  )
}
