import { useLocation } from 'react-router-dom'
import { useAuth } from '@/features/auth'
import { WorkspaceSidebar } from '@/widgets/workspace-sidebar'
import { useWorkspaceAnalysisStream } from '../model/useWorkspaceAnalysisStream'
import { HomeView } from './HomeView'
import { ResumesView } from './ResumesView'
import { ReposView } from './ReposView'
import { HistoryView } from './HistoryView'

type View = 'home' | 'resumes' | 'repos' | 'history'

function resolveView(pathname: string): View {
  if (pathname.startsWith('/workspace/history')) return 'history'
  if (pathname.startsWith('/workspace/resumes')) return 'resumes'
  if (pathname.startsWith('/workspace/repos')) return 'repos'
  return 'home'
}

export default function WorkspacePage() {
  // 분석 상태 실시간 구독 (SSE) — 어떤 뷰에 있든 완료 시 목록이 자동 갱신된다.
  useWorkspaceAnalysisStream()

  const { user } = useAuth()
  const { pathname } = useLocation()
  const view = resolveView(pathname)

  const meta = {
    home: {
      title: user ? `안녕하세요, ${user.githubUsername}님` : '대시보드',
      description: '오늘도 맞춤 모의 면접으로 실전 감각을 키워보세요.',
    },
    resumes: {
      title: '이력서',
      description: '이력서를 업로드하고 분석 결과를 확인하세요.',
    },
    repos: {
      title: '레포지토리',
      description: 'GitHub 레포를 등록하고 분석 결과를 확인하세요.',
    },
    history: {
      title: '면접 히스토리',
      description: '지난 면접 기록과 점수 추이를 확인하세요.',
    },
  }[view]

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg lg:flex-row">
      <WorkspaceSidebar />
      <main className="min-w-0 flex-1">
        <div className="mx-auto w-full max-w-content px-6 py-10 lg:px-12">
          <header className="mb-8">
            <h1 className="font-heading text-h4 font-bold text-fg-strong">
              {meta.title}
            </h1>
            <p className="mt-1 text-body text-fg-muted">{meta.description}</p>
          </header>

          {view === 'home' && <HomeView />}
          {view === 'resumes' && <ResumesView />}
          {view === 'repos' && <ReposView />}
          {view === 'history' && <HistoryView />}
        </div>
      </main>
    </div>
  )
}
