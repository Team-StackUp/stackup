import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { Stepper } from '@/shared/ui/Stepper'
import { Heading } from '@/shared/ui'
import type { JobCategory, SessionCreateRequest, SessionMode } from '@/domain/session'
import { ModeSelector } from './ModeSelector'
import { JobCategorySelector } from './JobCategorySelector'
import { ContextDocumentPicker } from './ContextDocumentPicker'
import type { DocOption } from './ContextDocumentPicker'

export function InterviewSetupForm({
  documents,
  documentsError = false,
  onRetryDocuments,
  onCreate,
  isSubmitting = false,
}: {
  documents: DocOption[]
  documentsError?: boolean
  onRetryDocuments?: () => void
  onCreate: (req: SessionCreateRequest) => void
  isSubmitting?: boolean
}) {
  const [title, setTitle] = useState('')
  const [mode, setMode] = useState<SessionMode | null>(null)
  const [jobCategories, setJobCategories] = useState<JobCategory[]>([])
  const [generalQuestionCount, setGeneralQuestionCount] = useState(3)
  const [maxFollowupsPerQuestion, setMaxFollowupsPerQuestion] = useState(2)
  const [maxQuestions, setMaxQuestions] = useState(10)
  const [selected, setSelected] = useState<number[]>([])
  const [companyName, setCompanyName] = useState('')
  const [jobDescription, setJobDescription] = useState('')

  const isJobTailored = mode === 'JOB_TAILORED'

  const toggle = (id: number) =>
    setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  const toggleJob = (job: JobCategory) =>
    setJobCategories((prev) =>
      prev.includes(job) ? prev.filter((x) => x !== job) : [...prev, job],
    )

  const valid =
    mode !== null &&
    jobCategories.length > 0 &&
    maxQuestions >= 2 &&
    maxQuestions <= 30 &&
    // 총 질문 상한은 최소한 일반질문 수 이상이어야 모순이 없다(상한 < 일반질문 수 차단).
    maxQuestions >= generalQuestionCount &&
    // 직무 맞춤 면접은 채용공고(JD)가 필수.
    (!isJobTailored || jobDescription.trim().length > 0)

  const submit = () => {
    if (mode === null || jobCategories.length === 0 || !valid) return
    const trimmedTitle = title.trim()
    onCreate({
      title: trimmedTitle || undefined,
      mode,
      jobCategories,
      generalQuestionCount,
      maxFollowupsPerQuestion,
      maxQuestions,
      contextDocumentIds: selected,
      targetCompanyName: isJobTailored ? companyName.trim() || undefined : undefined,
      targetJobDescription: isJobTailored ? jobDescription.trim() : undefined,
    })
  }

  return (
    <form
      className="mx-auto flex max-w-readable flex-col px-6 py-10"
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <section className="flex flex-col gap-3 border-t border-border py-7 first:border-t-0 first:pt-0">
        <label
          htmlFor="session-title"
          className="font-sans text-[18px] font-bold tracking-[-0.02em] text-fg"
        >
          면접 제목
          <span className="ml-1.5 text-caption font-normal text-fg-subtle">
            선택 · 미입력 시 '모의 면접'
          </span>
        </label>
        <input
          id="session-title"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={60}
          placeholder="예: 백엔드 기술 면접 2차"
          className="rounded-lg border border-border bg-surface-raised px-3.5 py-2.5 text-body text-fg placeholder:text-fg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--color-primary)] focus-visible:border-primary"
        />
      </section>
      <section className="flex flex-col gap-3 border-t border-border py-7 first:border-t-0 first:pt-0">
        <Heading level="sub">면접 모드</Heading>
        <ModeSelector
          value={mode}
          onChange={(m) => {
            setMode(m)
            // 직무 맞춤이 아니면 회사명·JD 입력값을 비운다(전송엔 이미 미포함이지만 상태도 정리).
            if (m !== 'JOB_TAILORED') {
              setCompanyName('')
              setJobDescription('')
            }
          }}
        />
      </section>
      {isJobTailored && (
        <section className="flex flex-col gap-4 border-t border-border py-7">
          <Heading level="sub">
            지원 회사 · 채용공고
            <span className="ml-1.5 text-caption font-normal text-fg-subtle">직무 맞춤 면접</span>
          </Heading>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="company-name" className="text-body text-fg">
              회사명 <span className="text-caption text-fg-subtle">선택</span>
            </label>
            <input
              id="company-name"
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              maxLength={200}
              placeholder="예: 토스, 우아한형제들"
              className="rounded-lg border border-border bg-surface-raised px-3.5 py-2.5 text-body text-fg placeholder:text-fg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--color-primary)] focus-visible:border-primary"
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="job-description" className="text-body text-fg">
              채용공고(JD) <span className="text-caption text-fg-subtle">필수 · 본문 붙여넣기</span>
            </label>
            <textarea
              id="job-description"
              value={jobDescription}
              onChange={(e) => setJobDescription(e.target.value)}
              maxLength={20000}
              rows={8}
              placeholder="채용공고의 자격요건·우대사항·주요업무를 붙여넣어 주세요. 이 내용으로 적합도·지원동기 질문과 직무 적합도 피드백이 생성됩니다."
              className="resize-y rounded-lg border border-border bg-surface-raised px-3.5 py-2.5 text-body text-fg placeholder:text-fg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--color-primary)] focus-visible:border-primary"
            />
          </div>
        </section>
      )}
      <section className="flex flex-col gap-3 border-t border-border py-7 first:border-t-0 first:pt-0">
        <Heading level="sub">
          직군
          <span className="ml-1.5 text-caption font-normal text-fg-subtle">복수 선택 가능</span>
        </Heading>
        <JobCategorySelector value={jobCategories} onToggle={toggleJob} />
      </section>
      <section className="flex flex-col gap-4 border-t border-border py-7">
        <Heading level="sub">면접 구성</Heading>
        <div className="flex items-center justify-between">
          <span className="text-body text-fg">
            일반질문 수
            <span className="ml-1.5 text-caption text-fg-subtle">서로 다른 주제</span>
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
            <span className="ml-1.5 text-caption text-fg-subtle">한 주제를 얼마나 파고들지</span>
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
            <span className="ml-1.5 text-caption text-fg-subtle">자기소개·일반질문 기준 · 꼬리질문 제외</span>
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
      <section className="flex flex-col gap-3 border-t border-border py-7 first:border-t-0 first:pt-0">
        <Heading level="sub">
          참고 문서
          <span className="ml-1.5 text-caption font-normal text-fg-subtle">선택</span>
        </Heading>
        <ContextDocumentPicker
          documents={documents}
          selected={selected}
          onToggle={toggle}
          loadFailed={documentsError}
          onRetry={onRetryDocuments}
        />
      </section>
      <div className="flex flex-col gap-3 border-t border-border pt-7">
        {maxQuestions < generalQuestionCount && (
          <p className="text-caption text-warning-700">
            총 질문 상한({maxQuestions})이 일반질문 수({generalQuestionCount})보다 작아요. 상한을 일반질문 수 이상으로 올려주세요.
          </p>
        )}
        {isJobTailored && jobDescription.trim().length === 0 && (
          <p className="text-caption text-warning-700">
            채용공고(JD)를 입력해야 직무 맞춤 면접을 생성할 수 있어요.
          </p>
        )}
        <Button type="submit" size="lg" loading={isSubmitting} disabled={!valid}>
          면접 생성
        </Button>
      </div>
    </form>
  )
}
