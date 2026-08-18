import { WorkspaceSection } from '@/widgets/workspace-section'
import { ResumeList, ResumeUploader, WebResumeForm } from '@/features/resume'
import { DocumentList } from '@/features/analysis'

export function ResumesView() {
  return (
    <div className="space-y-12">
      <WorkspaceSection
        eyebrow="01"
        title="이력서 · 링크 등록"
        description="PDF 이력서를 올리거나 포트폴리오·블로그 링크를 등록하면 AI가 분석해 면접 질문 풀에 반영합니다."
      >
        <div className="space-y-6">
          <ResumeUploader />
          {/* 파일과 링크는 같은 자료 목록으로 합쳐진다(서버에서도 같은 resume 도메인). */}
          <div className="border-t border-border pt-6">
            <WebResumeForm />
          </div>
          <ResumeList />
        </div>
      </WorkspaceSection>

      <WorkspaceSection
        eyebrow="02"
        title="분석 결과"
        description="등록한 이력서·링크의 요약과 추출된 기술 스택입니다."
      >
        <DocumentList sourceType="RESUME" />
      </WorkspaceSection>
    </div>
  )
}
