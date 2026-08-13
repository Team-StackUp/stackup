import { useLocation } from 'react-router-dom'
import { useAuth } from '@/features/auth'
import { PageHeader } from '@/shared/ui'
import { WorkspaceSidebar } from '@/widgets/workspace-sidebar'
import { useWorkspaceAnalysisStream } from '../model/useWorkspaceAnalysisStream'
import { HomeView } from './HomeView'
import { ResumesView } from './ResumesView'
import { ReposView } from './ReposView'
import { CoverLettersView } from './CoverLettersView'
import { HistoryView } from './HistoryView'

type View = 'home' | 'resumes' | 'repos' | 'cover-letters' | 'history'

function resolveView(pathname: string): View {
  if (pathname.startsWith('/workspace/history')) return 'history'
  if (pathname.startsWith('/workspace/resumes')) return 'resumes'
  if (pathname.startsWith('/workspace/repos')) return 'repos'
  if (pathname.startsWith('/workspace/cover-letters')) return 'cover-letters'
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
      eyebrow: '워크스페이스',
      title: user ? `안녕하세요, ${user.githubUsername}님` : '대시보드',
      description: '오늘도 맞춤 모의 면접으로 실전 감각을 키워보세요.',
    },
    resumes: {
      eyebrow: '워크스페이스',
      title: '이력서',
      description: '이력서를 업로드하고 분석 결과를 확인하세요.',
    },
    repos: {
      eyebrow: '워크스페이스',
      title: '레포지토리',
      description: 'GitHub 레포를 등록하고 분석 결과를 확인하세요.',
    },
    'cover-letters': {
      eyebrow: '워크스페이스',
      title: '자소서',
      description: '공채 자소서를 문항별로 입력하고 분석 결과를 확인하세요.',
    },
    history: {
      eyebrow: '워크스페이스',
      title: '면접 히스토리',
      description: '지난 면접 기록과 점수 추이를 확인하세요.',
    },
  }[view]

  return (
    <div className="flex min-h-svh flex-col bg-surface text-fg lg:flex-row">
      <WorkspaceSidebar />
      <main className="min-w-0 flex-1">
        <div className="mx-auto w-full max-w-content px-6 py-10 lg:px-12 lg:py-14">
          <PageHeader
            eyebrow={meta.eyebrow}
            title={meta.title}
            description={meta.description}
            className="mb-10"
          />

          {view === 'home' && <HomeView />}
          {view === 'resumes' && <ResumesView />}
          {view === 'repos' && <ReposView />}
          {view === 'cover-letters' && <CoverLettersView />}
          {view === 'history' && <HistoryView />}
        </div>
      </main>
    </div>
  )
}
