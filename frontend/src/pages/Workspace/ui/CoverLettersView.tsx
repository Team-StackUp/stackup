import { WorkspaceSection } from '@/widgets/workspace-section'
import { CoverLetterForm, CoverLetterList } from '@/features/cover-letter'
import { DocumentList } from '@/features/analysis'

export function CoverLettersView() {
  return (
    <div className="space-y-12">
      <WorkspaceSection
        title="내 자소서"
        description="공채 자소서를 문항별로 입력하면 AI가 분석해 면접 질문에 반영합니다."
      >
        <div className="space-y-6">
          <CoverLetterForm />
          <CoverLetterList />
        </div>
      </WorkspaceSection>

      <WorkspaceSection
        title="자소서 분석 결과"
        description="입력한 자소서의 요약과 핵심 키워드입니다."
      >
        <DocumentList sourceType="COVER_LETTER" />
      </WorkspaceSection>
    </div>
  )
}
