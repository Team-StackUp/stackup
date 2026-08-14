import { Link } from 'react-router-dom'
import { WorkspaceSection } from '@/widgets/workspace-section'
import { RepoList, RepoPicker } from '@/features/repo'
import { DocumentList } from '@/features/analysis'
import { useAuth } from '@/features/auth'
import { EmptyState } from '@/shared/ui'

export function ReposView() {
  const { user } = useAuth()
  // GitHub 토큰이 있어야 레포 목록을 불러올 수 있다. Google 계정은 토큰이 없으므로
  // 조회를 시도하지 않는다 — 요청을 보내 봐야 409 만 받고, 빈 목록으로 보여주면
  // "레포가 없다"는 잘못된 사실을 말하게 된다.
  const canUseRepos = user?.githubUsername != null

  if (!canUseRepos) {
    return (
      <EmptyState
        title="GitHub 계정에서 쓸 수 있는 기능이에요"
        description="레포지토리 분석은 GitHub 로그인으로 받은 권한이 필요합니다. 지금 계정으로는 이력서·자소서를 올려 면접을 진행할 수 있어요."
        action={
          <Link
            to="/workspace/resumes"
            className="inline-flex items-center rounded-lg bg-primary px-4 py-2.5 text-button font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
          >
            이력서 올리러 가기
          </Link>
        }
      />
    )
  }

  return (
    <div className="space-y-10">
      <WorkspaceSection
        eyebrow="01"
        title="레포 등록"
        description="등록한 레포를 기반으로 코드 맥락에 맞는 질문이 생성됩니다."
      >
        <div className="space-y-4">
          <RepoPicker />
          <RepoList />
        </div>
      </WorkspaceSection>

      <WorkspaceSection
        eyebrow="02"
        title="분석 결과"
        description="등록한 레포의 요약과 추출된 기술 스택입니다."
      >
        <DocumentList sourceType="REPOSITORY" />
      </WorkspaceSection>
    </div>
  )
}
