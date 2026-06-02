import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import type { JobCategory, SessionCreateRequest, SessionMode } from '@/domain/session'
import { ModeSelector } from './ModeSelector'
import { JobCategorySelector } from './JobCategorySelector'
import { QuestionCountField } from './QuestionCountField'
import { ContextDocumentPicker } from './ContextDocumentPicker'
import type { DocOption } from './ContextDocumentPicker'

export function InterviewSetupForm({
  documents,
  onCreate,
  isSubmitting = false,
}: {
  documents: DocOption[]
  onCreate: (req: SessionCreateRequest) => void
  isSubmitting?: boolean
}) {
  const [mode, setMode] = useState<SessionMode | null>(null)
  const [jobCategory, setJobCategory] = useState<JobCategory | null>(null)
  const [maxQuestions, setMaxQuestions] = useState(5)
  const [selected, setSelected] = useState<number[]>([])

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  const valid = mode !== null && jobCategory !== null && maxQuestions >= 2 && maxQuestions <= 30

  const submit = () => {
    if (mode === null || jobCategory === null || !valid) return
    onCreate({ mode, jobCategory, maxQuestions, contextDocumentIds: selected })
  }

  return (
    <form
      className="mx-auto flex max-w-readable flex-col gap-6 px-4 py-8"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <section className="flex flex-col gap-2">
        <h2 className="text-h6 text-fg">면접 모드</h2>
        <ModeSelector value={mode} onChange={setMode} />
      </section>
      <section className="flex flex-col gap-2">
        <h2 className="text-h6 text-fg">직군</h2>
        <JobCategorySelector value={jobCategory} onChange={setJobCategory} />
      </section>
      <section className="flex flex-col gap-2">
        <h2 className="text-h6 text-fg">질문 수</h2>
        <QuestionCountField value={maxQuestions} onChange={setMaxQuestions} />
      </section>
      <section className="flex flex-col gap-2">
        <h2 className="text-h6 text-fg">참고 문서 (선택)</h2>
        <ContextDocumentPicker documents={documents} selected={selected} onToggle={toggle} />
      </section>
      <Button type="submit" size="lg" loading={isSubmitting} disabled={!valid}>
        면접 생성
      </Button>
    </form>
  )
}
