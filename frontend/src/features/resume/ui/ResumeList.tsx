import { useState } from 'react'
import { useAnalysisProgress } from '@/shared/hooks'
import { ConfirmDialog, EmptyState, ListSkeleton, StatusBadge, type StatusTone, QueryError } from '@/shared/ui'
import { useDeleteResume, useResumes } from '../model/useResumes'
import { formatFileSize } from '../lib/format'
import type { Resume, ResumeStatus } from '../model/types'

const STATUS_META: Record<ResumeStatus, { tone: StatusTone; label: string }> = {
  PENDING: { tone: 'info', label: '대기 중' },
  ANALYZING: { tone: 'warning', label: '분석 중' },
  ANALYZED: { tone: 'success', label: '분석 완료' },
  FAILED: { tone: 'danger', label: '분석 실패' },
}

export function ResumeList() {
  const { data = [], isPending, isError, refetch } = useResumes()
  const remove = useDeleteResume()

  if (isPending) {
    return <ListSkeleton label="이력서를 불러오는 중…" />
  }
  if (isError) {
    return <QueryError message="이력서를 불러오지 못했습니다." onRetry={() => refetch()} />
  }
  if (data.length === 0) {
    return (
      <EmptyState
        title="아직 등록한 자료가 없어요"
        description="위에서 PDF 이력서를 올리거나 포트폴리오 링크를 등록하면 분석이 자동으로 시작됩니다."
      />
    )
  }

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {data.map((resume) => (
        <ResumeCard
          key={resume.id}
          resume={resume}
          deleting={remove.isPending}
          onDelete={() => remove.mutate(resume.id)}
        />
      ))}
    </ul>
  )
}

function ResumeCard({
  resume,
  deleting,
  onDelete,
}: {
  resume: Resume
  deleting: boolean
  onDelete: () => void
}) {
  const meta = STATUS_META[resume.status]
  const isWeb = resume.fileType === 'WEB'
  const [confirmOpen, setConfirmOpen] = useState(false)
  const progress = useAnalysisProgress('RESUME', resume.id)
  const showProgress =
    !!progress &&
    (resume.status === 'ANALYZING' || resume.status === 'PENDING')

  return (
    <li className="group flex items-start gap-3 rounded-xl border border-border bg-surface-raised p-4 transition-colors duration-fast hover:border-border-strong">
      <span
        aria-hidden
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-fg"
      >
        {isWeb ? <LinkIcon /> : <FileIcon />}
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          {isWeb && resume.sourceUrl ? (
            <a
              href={resume.sourceUrl}
              target="_blank"
              rel="noreferrer noopener"
              title={resume.sourceUrl}
              className="min-w-0 flex-1 truncate text-body font-semibold text-fg-strong underline decoration-border-strong underline-offset-2 hover:decoration-primary"
            >
              {resume.originalFilename}
            </a>
          ) : (
            <p className="min-w-0 flex-1 truncate text-body font-semibold text-fg-strong">
              {resume.originalFilename}
            </p>
          )}
          <button
            type="button"
            disabled={deleting}
            onClick={() => setConfirmOpen(true)}
            aria-label={isWeb ? '링크 삭제' : '이력서 삭제'}
            className="shrink-0 rounded-md p-1 text-fg-subtle opacity-0 transition-colors duration-fast hover:bg-surface hover:text-danger-700 focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-40"
          >
            <TrashIcon />
          </button>
        </div>

        <ConfirmDialog
          open={confirmOpen}
          title={isWeb ? '링크를 삭제하시겠습니까?' : '이력서를 삭제하시겠습니까?'}
          description={`'${resume.originalFilename}'을(를) 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
          confirmLabel="삭제"
          danger
          loading={deleting}
          onConfirm={onDelete}
          onCancel={() => setConfirmOpen(false)}
        />

        <div className="mt-2 flex items-center gap-2">
          <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
          <span className="truncate text-caption text-fg-muted">
            {showProgress
              ? progress.message
              : isWeb
                ? '웹 링크'
                : formatFileSize(resume.fileSize)}
          </span>
        </div>
      </div>
    </li>
  )
}

function FileIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 2.5h6l4 4V16a1.5 1.5 0 0 1-1.5 1.5h-8A1.5 1.5 0 0 1 4 16V4A1.5 1.5 0 0 1 5 2.5Z" />
      <path d="M11 2.5V6a1 1 0 0 0 1 1h3" />
      <path d="M7 10.5h6M7 13.5h4" />
    </svg>
  )
}

function LinkIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M8.5 11.5 11.5 8.5" />
      <path d="M7.5 13.5 6 15a2.8 2.8 0 0 1-4-4l1.5-1.5" />
      <path d="M12.5 6.5 14 5a2.8 2.8 0 0 1 4 4l-1.5 1.5" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="16"
      height="16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M4 6h12M8 6V4.5A1 1 0 0 1 9 3.5h2a1 1 0 0 1 1 1V6m1.5 0-.5 9a1.5 1.5 0 0 1-1.5 1.4H7.5A1.5 1.5 0 0 1 6 15l-.5-9" />
    </svg>
  )
}
