import { WorkspaceSection } from '@/widgets/workspace-section'
import { RepoList, RepoPicker } from '@/features/repo'
import { DocumentList } from '@/features/analysis'

export function ReposView() {
  return (
    <div className="space-y-10">
      <WorkspaceSection
        title="내 GitHub 레포지토리"
        description="등록한 레포를 기반으로 코드 맥락에 맞는 질문이 생성됩니다."
      >
        <div className="space-y-4">
          <RepoPicker />
          <RepoList />
        </div>
      </WorkspaceSection>

      <WorkspaceSection
        title="레포지토리 분석 결과"
        description="등록한 레포의 요약과 추출된 기술 스택입니다."
      >
        <DocumentList sourceType="REPOSITORY" />
      </WorkspaceSection>
    </div>
  )
}
