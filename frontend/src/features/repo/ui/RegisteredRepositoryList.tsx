import { isApiError } from '@/shared/api'
import {
  useDeleteRepoMutation,
  useRegisteredReposQuery,
} from '@/features/repo/model/useRepos'
import type {
  RegisteredRepositoryResponse,
  RepositoryStatus,
} from '@/features/repo/model/types'

type Props = {
  onBrowseCandidates: () => void
}

export function RegisteredRepositoryList({ onBrowseCandidates }: Props) {
  const { data, isLoading, isError, error, refetch } = useRegisteredReposQuery()

  if (isLoading) return <ListSkeleton />
  if (isError) {
    const message = isApiError(error)
      ? error.message
      : '레포지토리 목록을 불러오지 못했습니다.'
    return (
      <div className="rounded-xl border border-border bg-surface-raised p-8 text-center">
        <p role="alert" className="text-body text-danger-700">
          {message}
        </p>
        <button
          type="button"
          onClick={() => refetch()}
          className="mt-4 inline-flex items-center px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast"
        >
          다시 시도
        </button>
      </div>
    )
  }

  const repos = data?.content ?? []
  if (repos.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border-strong bg-surface p-10 text-center">
        <p className="text-body text-fg-muted">
          아직 등록된 레포지토리가 없습니다.
        </p>
        <p className="text-caption text-fg-subtle mt-2">
          GitHub에서 분석할 레포를 가져와 등록해보세요.
        </p>
        <button
          type="button"
          onClick={onBrowseCandidates}
          className="mt-4 inline-flex items-center px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast"
        >
          GitHub에서 가져오기
        </button>
      </div>
    )
  }

  return (
    <ul className="grid gap-3">
      {repos.map((repo) => (
        <li key={repo.id}>
          <RegisteredRepoCard repo={repo} />
        </li>
      ))}
    </ul>
  )
}

function RegisteredRepoCard({ repo }: { repo: RegisteredRepositoryResponse }) {
  const remove = useDeleteRepoMutation()
  const handleDelete = () => {
    const ok = window.confirm(
      `'${repo.repoFullName}' 레포지토리 등록을 해제하시겠습니까?`,
    )
    if (!ok) return
    remove.mutate(repo.id)
  }
  return (
    <article className="rounded-xl bg-surface-raised border border-border shadow-sm p-5 flex items-center gap-4">
      <RepoIcon />
      <div className="min-w-0 flex-1">
        <a
          href={repo.repoUrl}
          target="_blank"
          rel="noreferrer noopener"
          className="text-body text-fg-strong font-semibold hover:underline truncate inline-block max-w-full"
        >
          {repo.repoFullName}
        </a>
        <p className="text-caption text-fg-muted mt-1">
          기본 브랜치 {repo.defaultBranch} · {syncLabel(repo)}
        </p>
      </div>
      <StatusBadge status={repo.status} />
      <button
        type="button"
        onClick={handleDelete}
        disabled={remove.isPending}
        aria-busy={remove.isPending}
        aria-label={`${repo.repoFullName} 삭제`}
        className="inline-flex items-center px-3 py-1.5 rounded-pill border border-border text-button text-fg-muted hover:text-danger-700 hover:border-danger-500 transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {remove.isPending ? '삭제 중…' : '삭제'}
      </button>
    </article>
  )
}

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="레포 목록 로딩 중" className="grid gap-3">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="rounded-xl bg-surface-raised border border-border shadow-sm p-5 flex items-center gap-4"
        >
          <div className="w-10 h-10 rounded-md bg-sage-100 animate-pulse" />
          <div className="flex-1 space-y-2">
            <div className="h-4 w-1/2 rounded bg-sage-100 animate-pulse" />
            <div className="h-3 w-1/3 rounded bg-sage-100 animate-pulse" />
          </div>
          <div className="h-6 w-20 rounded-pill bg-sage-100 animate-pulse" />
        </li>
      ))}
    </ul>
  )
}

const STATUS_LABEL: Record<RepositoryStatus, string> = {
  PENDING: '분석 대기',
  ANALYZING: '분석 중',
  ANALYZED: '분석 완료',
  FAILED: '분석 실패',
}

const STATUS_CLASS: Record<RepositoryStatus, string> = {
  PENDING: 'bg-info-50 text-info-700',
  ANALYZING: 'bg-info-50 text-info-700',
  ANALYZED: 'bg-success-50 text-success-700',
  FAILED: 'bg-danger-50 text-danger-700',
}

function StatusBadge({ status }: { status: RepositoryStatus }) {
  const inProgress = status === 'PENDING' || status === 'ANALYZING'
  return (
    <span
      className={[
        'inline-flex items-center gap-1.5 px-3 py-1 rounded-pill text-caption font-mono',
        STATUS_CLASS[status],
      ].join(' ')}
    >
      {inProgress ? <Spinner /> : null}
      {STATUS_LABEL[status]}
    </span>
  )
}

function Spinner() {
  return (
    <svg
      aria-hidden
      width="10"
      height="10"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      className="animate-spin"
    >
      <circle cx="12" cy="12" r="9" strokeOpacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" strokeLinecap="round" />
    </svg>
  )
}

function RepoIcon() {
  return (
    <div className="w-10 h-10 rounded-md bg-sage-100 text-fg-strong flex items-center justify-center">
      <svg
        aria-hidden
        viewBox="0 0 16 16"
        width="18"
        height="18"
        fill="currentColor"
      >
        <path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z" />
      </svg>
    </div>
  )
}

function syncLabel(repo: RegisteredRepositoryResponse): string {
  if (repo.lastSyncedAt) return `최근 동기화 ${relativeTime(repo.lastSyncedAt)}`
  return `등록 ${relativeTime(repo.createdAt)}`
}

function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  if (diffMs < 0) return '방금 전'
  const sec = diffMs / 1000
  if (sec < 60) return '방금 전'
  const min = sec / 60
  if (min < 60) return `${Math.floor(min)}분 전`
  const hr = min / 60
  if (hr < 24) return `${Math.floor(hr)}시간 전`
  const day = hr / 24
  if (day < 7) return `${Math.floor(day)}일 전`
  return new Date(iso).toLocaleDateString('ko-KR')
}
