import { useNavigate } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { useDocuments } from '@/features/analysis'
import { InterviewSetupForm, useCreateSession } from '@/features/interview'
import type { DocOption } from '@/features/interview'

export default function InterviewSetupPage() {
  const navigate = useNavigate()
  const { data: documents = [] } = useDocuments()
  const createSession = useCreateSession()

  const options: DocOption[] = documents
    .filter((d) => d.analysisStatus === 'ANALYZED')
    .map((d) => ({
      id: d.id,
      label: d.summary?.slice(0, 40) ?? `${d.sourceType} #${d.sourceId}`,
      sourceType: d.sourceType,
    }))

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="flex-1">
        <div className="mx-auto max-w-readable px-4 pt-10">
          <h1 className="text-h4 text-fg">새 모의 면접</h1>
          <p className="mt-2 text-body text-fg-muted">
            모드와 직군을 선택하면 AI가 맞춤 질문을 생성합니다.
          </p>
        </div>
        <InterviewSetupForm
          documents={options}
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
