import { WorkspaceSection } from '@/widgets/workspace-section'
import { ResumeList, ResumeUploader } from '@/features/resume'
import { DocumentList } from '@/features/analysis'

export function ResumesView() {
  return (
    <div className="space-y-12">
      <WorkspaceSection
        title="내 이력서"
        description="PDF 이력서를 업로드하면 AI가 분석해 면접 질문 풀에 반영합니다."
      >
        <div className="space-y-6">
          <ResumeUploader />
          <ResumeList />
        </div>
      </WorkspaceSection>

      <WorkspaceSection
        title="이력서 분석 결과"
        description="업로드한 이력서의 요약과 추출된 기술 스택입니다."
      >
        <DocumentList sourceType="RESUME" />
      </WorkspaceSection>
    </div>
  )
}
