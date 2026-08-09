import { useState } from 'react'
import { isApiError } from '@/shared/api'
import { useAnalysisProgress } from '@/shared/hooks'
import { ConfirmDialog, EmptyState, ListSkeleton, StatusBadge, type StatusTone } from '@/shared/ui'
import { useCoverLetters, useDeleteCoverLetter } from '../model/useCoverLetters'
import type { CoverLetter, CoverLetterStatus } from '../model/types'

const STATUS_META: Record<CoverLetterStatus, { tone: StatusTone; label: string }> = {
  PENDING: { tone: 'info', label: '대기 중' },
  ANALYZING: { tone: 'warning', label: '분석 중' },
  ANALYZED: { tone: 'success', label: '분석 완료' },
  FAILED: { tone: 'danger', label: '분석 실패' },
}

export function CoverLetterList() {
  const { data = [], isPending, isError, error } = useCoverLetters()
  const remove = useDeleteCoverLetter()

  if (isPending) {
    return <ListSkeleton label="자소서를 불러오는 중…" />
  }
  if (isError) {
    return (
      <p className="text-body text-danger-700">
        {isApiError(error) ? error.message : '자소서를 불러오지 못했습니다.'}
      </p>
    )
  }
  if (data.length === 0) {
    return (
      <EmptyState
        title="아직 등록한 자소서가 없어요"
        description="위에서 문항별로 자소서를 입력하면 분석이 자동으로 시작됩니다."
      />
    )
  }

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {data.map((cl) => (
        <CoverLetterCard
          key={cl.id}
          coverLetter={cl}
          deleting={remove.isPending}
          onDelete={() => cl.id != null && remove.mutate(cl.id)}
        />
      ))}
    </ul>
  )
}

function CoverLetterCard({
  coverLetter,
  deleting,
  onDelete,
}: {
  coverLetter: CoverLetter
  deleting: boolean
  onDelete: () => void
}) {
  const status: CoverLetterStatus = coverLetter.status ?? 'PENDING'
  const meta = STATUS_META[status]
  const [confirmOpen, setConfirmOpen] = useState(false)
  const progress = useAnalysisProgress('COVER_LETTER', coverLetter.id ?? -1)
  const showProgress = !!progress && (status === 'ANALYZING' || status === 'PENDING')
  const itemCount = coverLetter.items?.length ?? 0
  const title = coverLetter.title?.trim() || `자소서 #${coverLetter.id ?? ''}`

  return (
    <li className="group flex items-start gap-3 rounded-2xl border border-border bg-surface-raised p-4 shadow-sm transition-colors duration-fast hover:border-border-strong">
      <span
        aria-hidden
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-fg"
      >
        <DocIcon />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <p className="min-w-0 flex-1 truncate text-body font-semibold text-fg-strong">
            {title}
          </p>
          <button
            type="button"
            disabled={deleting}
            onClick={() => setConfirmOpen(true)}
            aria-label="자소서 삭제"
            className="shrink-0 rounded-md p-1 text-fg-subtle opacity-0 transition-colors duration-fast hover:bg-surface hover:text-danger-700 focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-40"
          >
            <TrashIcon />
          </button>
        </div>

        <ConfirmDialog
          open={confirmOpen}
          title="자소서를 삭제하시겠습니까?"
          description={`'${title}'을(를) 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
          confirmLabel="삭제"
          danger
          loading={deleting}
          onConfirm={onDelete}
          onCancel={() => setConfirmOpen(false)}
        />

        <div className="mt-2 flex items-center gap-2">
          <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
          <span className="truncate text-caption text-fg-muted">
            {showProgress ? progress.message : `문항 ${itemCount}개`}
          </span>
        </div>
      </div>
    </li>
  )
}

function DocIcon() {
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
