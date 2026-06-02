import { Link } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { WorkspaceProfileCard } from '@/widgets/workspace-profile-card'
import { WorkspaceSection } from '@/widgets/workspace-section'
import { ResumeList, ResumeUploader } from '@/features/resume'
import { RepoList, RepoPicker } from '@/features/repo'
import { DocumentList } from '@/features/analysis'
import { Button } from '@/shared/ui/Button'
import { useWorkspaceAnalysisStream } from '../model/useWorkspaceAnalysisStream'

export default function WorkspacePage() {
  // 분석 상태 실시간 구독 (SSE) — 완료되면 목록이 자동 갱신된다.
  useWorkspaceAnalysisStream()

  return (
    <div className="min-h-svh bg-bg text-fg flex flex-col">
      <SiteNav />
      <main className="flex-1 mx-auto w-full max-w-content px-6 lg:px-12 py-10 space-y-10">
        <WorkspaceProfileCard />

        <WorkspaceSection
          title="모의 면접"
          description="분석된 이력서·레포를 바탕으로 맞춤 모의 면접을 시작하세요."
          action={
            <Link to="/sessions/new">
              <Button size="lg">새 면접 시작</Button>
            </Link>
          }
        >
          <p className="text-body text-fg-muted">
            면접 모드와 직군을 고르면 AI가 질문을 생성하고, 실시간으로 답변을 주고받습니다.
          </p>
        </WorkspaceSection>

        <WorkspaceSection
          title="내 이력서"
          description="PDF 이력서를 업로드하면 AI가 분석해 면접 질문 풀에 반영합니다."
          action={<ResumeUploader />}
        >
          <ResumeList />
        </WorkspaceSection>

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
          title="분석 결과"
          description="이력서·레포 분석이 완료되면 요약과 기술 스택이 표시됩니다."
        >
          <DocumentList />
        </WorkspaceSection>
      </main>
      <SiteFooter />
    </div>
  )
}
