import { useState } from 'react'
import { isApiError } from '@/shared/api'
import { useAnalysisProgress } from '@/shared/hooks'
import { ConfirmDialog, EmptyState, ListSkeleton, StatusBadge, type StatusTone } from '@/shared/ui'
import {
  useDeleteRepository,
  useRegisteredRepositories,
} from '../model/useRepositories'
import type { RegisteredRepository, RepositoryStatus } from '../model/types'

const STATUS_META: Record<
  RepositoryStatus,
  { tone: StatusTone; label: string }
> = {
  PENDING: { tone: 'info', label: '대기 중' },
  ANALYZING: { tone: 'warning', label: '분석 중' },
  ANALYZED: { tone: 'success', label: '분석 완료' },
  FAILED: { tone: 'danger', label: '분석 실패' },
}

export function RepoList() {
  const { data = [], isPending, isError, error } = useRegisteredRepositories()
  const remove = useDeleteRepository()

  if (isPending) {
    return <ListSkeleton label="레포지토리를 불러오는 중…" />
  }
  if (isError) {
    return (
      <p className="text-body text-danger-700">
        {isApiError(error) ? error.message : '레포지토리를 불러오지 못했습니다.'}
      </p>
    )
  }
  if (data.length === 0) {
    return (
      <EmptyState
        title="아직 등록된 레포지토리가 없어요"
        description="위에서 GitHub 레포를 가져오면 분석이 자동으로 시작됩니다."
      />
    )
  }

  return (
    <ul className="grid gap-3 sm:grid-cols-2">
      {data.map((repo) => (
        <RepoCard
          key={repo.id}
          repo={repo}
          deleting={remove.isPending}
          onDelete={() => remove.mutate(repo.id)}
        />
      ))}
    </ul>
  )
}

function RepoCard({
  repo,
  deleting,
  onDelete,
}: {
  repo: RegisteredRepository
  deleting: boolean
  onDelete: () => void
}) {
  const meta = STATUS_META[repo.status]
  const [confirmOpen, setConfirmOpen] = useState(false)
  const progress = useAnalysisProgress('REPOSITORY', repo.id)
  // 진행 문구는 분석 진행 중일 때만 의미 있다. 완료/실패 시 store 가 clear 되지만 방어적으로 가드.
  const showProgress =
    !!progress && (repo.status === 'ANALYZING' || repo.status === 'PENDING')

  return (
    <li className="group flex items-start gap-3 rounded-2xl border border-border bg-surface-raised p-4 shadow-sm transition-colors duration-fast hover:border-border-strong">
      <span
        aria-hidden
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-sage-100 text-primary"
      >
        <RepoIcon />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <a
            href={repo.repoUrl}
            target="_blank"
            rel="noreferrer"
            className="min-w-0 flex-1 truncate text-body font-semibold text-fg-strong hover:text-primary hover:underline"
          >
            {repo.repoFullName}
          </a>
          <button
            type="button"
            disabled={deleting}
            onClick={() => setConfirmOpen(true)}
            aria-label="레포지토리 삭제"
            className="shrink-0 rounded-md p-1 text-fg-subtle opacity-0 transition-colors duration-fast hover:bg-surface hover:text-danger-700 focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-40"
          >
            <TrashIcon />
          </button>
        </div>

        <ConfirmDialog
          open={confirmOpen}
          title="레포지토리를 삭제하시겠습니까?"
          description={`'${repo.repoFullName}'을(를) 목록에서 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
          confirmLabel="삭제"
          danger
          loading={deleting}
          onConfirm={onDelete}
          onCancel={() => setConfirmOpen(false)}
        />

        <div className="mt-2 flex items-center gap-2">
          <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
          <span className="truncate text-caption text-fg-muted">
            {showProgress ? progress.message : (repo.defaultBranch ?? 'main')}
          </span>
        </div>
      </div>
    </li>
  )
}

function RepoIcon() {
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
      <circle cx="6" cy="5" r="2" />
      <circle cx="6" cy="15" r="2" />
      <circle cx="14" cy="7.5" r="2" />
      <path d="M6 7v6M6 13a4 4 0 0 0 4-4h2.2" />
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
