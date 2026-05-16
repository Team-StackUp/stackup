import { useState } from 'react'
import { isApiError } from '@/shared/api'
import {
  useCandidateReposQuery,
  useRegisterRepoMutation,
} from '@/features/repo/model/useRepos'
import type { CandidateRepositoryResponse } from '@/features/repo/model/types'

export function CandidateRepositoryList() {
  const { data, isLoading, isError, error, refetch } = useCandidateReposQuery()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (isLoading) return <ListSkeleton />
  if (isError) {
    const message = isApiError(error)
      ? error.message
      : 'GitHub 레포 후보를 불러오지 못했습니다.'
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

  const candidates = data?.content ?? []
  if (candidates.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border-strong bg-surface p-10 text-center">
        <p className="text-body text-fg-muted">
          가져올 수 있는 GitHub 레포가 없습니다.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {errorMessage ? (
        <p role="alert" className="text-caption text-danger-700">
          {errorMessage}
        </p>
      ) : null}
      <ul className="grid gap-3">
        {candidates.map((candidate) => (
          <li key={candidate.githubRepoId}>
            <CandidateCard
              candidate={candidate}
              onError={setErrorMessage}
              onClearError={() => setErrorMessage(null)}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}

function CandidateCard({
  candidate,
  onError,
  onClearError,
}: {
  candidate: CandidateRepositoryResponse
  onError: (message: string) => void
  onClearError: () => void
}) {
  const register = useRegisterRepoMutation()
  const disabled =
    candidate.alreadyRegistered || candidate.private || register.isPending

  const handleClick = () => {
    onClearError()
    register.mutate(candidate.githubRepoId, {
      onError: (err) => {
        onError(
          isApiError(err) ? err.message : '등록 중 오류가 발생했습니다.',
        )
      },
    })
  }

  return (
    <article className="rounded-xl bg-surface-raised border border-border shadow-sm p-5 flex items-start gap-4">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 flex-wrap">
          <a
            href={candidate.htmlUrl}
            target="_blank"
            rel="noreferrer noopener"
            className="text-body text-fg-strong font-semibold hover:underline truncate"
          >
            {candidate.fullName}
          </a>
          {candidate.private ? (
            <span className="inline-flex items-center px-2 py-0.5 rounded-pill text-caption font-mono bg-warning-50 text-warning-700">
              Private
            </span>
          ) : null}
          {candidate.alreadyRegistered ? (
            <span className="inline-flex items-center px-2 py-0.5 rounded-pill text-caption font-mono bg-success-50 text-success-700">
              이미 등록됨
            </span>
          ) : null}
        </div>
        <p className="text-caption text-fg-muted mt-1.5 line-clamp-2">
          {candidate.description ?? '설명 없음'}
        </p>
        <p className="text-caption text-fg-subtle mt-1 font-mono">
          기본 브랜치 {candidate.defaultBranch}
        </p>
      </div>
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled}
        aria-busy={register.isPending}
        className="shrink-0 inline-flex items-center px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {register.isPending
          ? '등록 중…'
          : candidate.alreadyRegistered
            ? '등록됨'
            : '등록'}
      </button>
    </article>
  )
}

function ListSkeleton() {
  return (
    <ul
      aria-busy="true"
      aria-label="GitHub 후보 로딩 중"
      className="grid gap-3"
    >
      {[0, 1, 2, 3].map((i) => (
        <li
          key={i}
          className="rounded-xl bg-surface-raised border border-border shadow-sm p-5"
        >
          <div className="h-4 w-1/2 rounded bg-sage-100 animate-pulse" />
          <div className="mt-2 h-3 w-3/4 rounded bg-sage-100 animate-pulse" />
        </li>
      ))}
    </ul>
  )
}
