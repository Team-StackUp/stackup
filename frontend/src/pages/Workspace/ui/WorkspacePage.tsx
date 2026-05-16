import { ResumeList, UploadResumeButton } from '@/features/resume'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { WorkspaceProfileCard } from '@/widgets/workspace-profile-card'
import { WorkspaceSection } from '@/widgets/workspace-section'

//이 컴포넌트는 아직 초기 프로토타입 입니다.
export default function WorkspacePage() {
  return (
    <div className="min-h-svh bg-bg text-fg flex flex-col">
      <SiteNav />
      <main className="flex-1 mx-auto w-full max-w-content px-6 lg:px-12 py-10 space-y-10">
        <WorkspaceProfileCard />

        <WorkspaceSection
          title="내 이력서"
          description="PDF 이력서를 업로드하면 AI가 분석해 면접 질문 풀에 반영합니다."
          action={<UploadResumeButton />}
        >
          <ResumeList />
        </WorkspaceSection>

        <WorkspaceSection
          title="내 GitHub 레포지토리"
          description="등록한 레포를 기반으로 코드 맥락에 맞는 질문이 생성됩니다."
        >
          <EmptyRepoSlot />
        </WorkspaceSection>
      </main>
      <SiteFooter />
    </div>
  )
}

function EmptyRepoSlot() {
  return (
    <div className="rounded-xl border border-dashed border-border-strong bg-surface p-10 text-center">
      <p className="text-body text-fg-muted">아직 등록된 레포지토리가 없습니다.</p>
      <p className="text-caption text-fg-subtle mt-2">
        다음 단계에서 GitHub 레포 가져오기가 가능해집니다.
      </p>
    </div>
  )
}
