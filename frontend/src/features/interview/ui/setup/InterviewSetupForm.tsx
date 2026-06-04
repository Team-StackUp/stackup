import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { Stepper } from '@/shared/ui/Stepper'
import type { JobCategory, SessionCreateRequest, SessionMode } from '@/domain/session'
import { ModeSelector } from './ModeSelector'
import { JobCategorySelector } from './JobCategorySelector'
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
  const [jobCategories, setJobCategories] = useState<JobCategory[]>([])
  const [generalQuestionCount, setGeneralQuestionCount] = useState(3)
  const [maxFollowupsPerQuestion, setMaxFollowupsPerQuestion] = useState(2)
  const [maxQuestions, setMaxQuestions] = useState(10)
  const [selected, setSelected] = useState<number[]>([])

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  const toggleJob = (job: JobCategory) =>
    setJobCategories((prev) =>
      prev.includes(job) ? prev.filter((x) => x !== job) : [...prev, job],
    )

  const valid =
    mode !== null && jobCategories.length > 0 && maxQuestions >= 2 && maxQuestions <= 30

  const submit = () => {
    if (mode === null || jobCategories.length === 0 || !valid) return
    onCreate({
      mode,
      jobCategories,
      generalQuestionCount,
      maxFollowupsPerQuestion,
      maxQuestions,
      contextDocumentIds: selected,
    })
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
        <h2 className="text-h6 text-fg">
          직군
          <span className="ml-1 text-caption text-fg-muted">복수 선택 가능</span>
        </h2>
        <JobCategorySelector value={jobCategories} onToggle={toggleJob} />
      </section>
      <section className="flex flex-col gap-3">
        <h2 className="text-h6 text-fg">면접 구성</h2>
        <div className="flex items-center justify-between">
          <span className="text-body text-fg">
            일반질문 수
            <span className="ml-1 text-caption text-fg-muted">서로 다른 주제</span>
          </span>
          <Stepper
            ariaLabel="일반질문 수"
            value={generalQuestionCount}
            onChange={setGeneralQuestionCount}
            min={1}
            max={15}
          />
        </div>
        <div className="flex items-center justify-between">
          <span className="text-body text-fg">
            질문당 꼬리질문
            <span className="ml-1 text-caption text-fg-muted">깊이</span>
          </span>
          <Stepper
            ariaLabel="질문당 최대 꼬리질문"
            value={maxFollowupsPerQuestion}
            onChange={setMaxFollowupsPerQuestion}
            min={0}
            max={10}
          />
        </div>
        <div className="flex items-center justify-between">
          <span className="text-body text-fg">
            총 질문 상한
            <span className="ml-1 text-caption text-fg-muted">안전장치</span>
          </span>
          <Stepper
            ariaLabel="총 질문 상한"
            value={maxQuestions}
            onChange={setMaxQuestions}
            min={2}
            max={30}
          />
        </div>
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
