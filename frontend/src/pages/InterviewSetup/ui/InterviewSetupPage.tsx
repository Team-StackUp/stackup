import { Link, useNavigate } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { useDocuments } from '@/features/analysis'
import { InterviewSetupForm, useCreateSession } from '@/features/interview'
import type { DocOption } from '@/features/interview'
import { documentSourceLabel } from '@/domain/rag'
import { PageHeader } from '@/shared/ui'

export default function InterviewSetupPage() {
  const navigate = useNavigate()
  const { data: documents = [], isError: documentsError, refetch: refetchDocuments } = useDocuments()
  const createSession = useCreateSession()

  const options: DocOption[] = documents
    .filter((d) => d.analysisStatus === 'ANALYZED')
    .map((d) => ({
      id: d.id,
      label: d.summary?.slice(0, 40) ?? `${documentSourceLabel(d.sourceType)} #${d.sourceId}`,
      sourceType: d.sourceType,
    }))

  return (
    <div className="flex min-h-svh flex-col bg-surface-raised text-fg">
      <SiteNav />
      <main className="flex-1">
        <div className="mx-auto max-w-readable px-6 pt-10 lg:pt-14">
          <PageHeader
            eyebrow="새 면접"
            title="새 모의 면접"
            description="모드와 직군을 선택하면 AI가 맞춤 질문을 생성합니다."
            above={
              <Link
                to="/workspace"
                className="inline-flex items-center gap-1 text-button font-medium text-fg-muted transition-colors duration-fast hover:text-fg-strong"
              >
                <span aria-hidden>←</span> 워크스페이스로
              </Link>
            }
          />
        </div>
        <InterviewSetupForm
          documents={options}
          documentsError={documentsError}
          onRetryDocuments={() => void refetchDocuments()}
          isSubmitting={createSession.isPending}
          onCreate={(req) =>
            createSession.mutate(req, {
              onSuccess: (session) => navigate(`/sessions/${session.id}`),
            })
          }
        />
      </main>
      <SiteFooter />
    </div>
  )
}
