import { useNavigate } from 'react-router-dom'
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
    <InterviewSetupForm
      documents={options}
      isSubmitting={createSession.isPending}
      onCreate={(req) =>
        createSession.mutate(req, {
          onSuccess: (session) => navigate(`/sessions/${session.id}`),
        })
      }
    />
  )
}
