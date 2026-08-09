import { useState } from 'react'
import { isApiError } from '@/shared/api'
import {
  useCandidateRepositories,
  useRegisterRepository,
} from '../model/useRepositories'
import type { CandidateRepository } from '../model/types'

const PER_PAGE = 30

export function RepoPicker() {
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(1)
  const candidates = useCandidateRepositories(page, PER_PAGE, open)
  const register = useRegisterRepository()
  const [error, setError] = useState<string | null>(null)

  const onRegister = (repo: CandidateRepository) => {
    setError(null)
    register.mutate(
      { githubRepoId: repo.githubRepoId, fullName: repo.fullName },
      {
        onError: (e) =>
          setError(isApiError(e) ? e.message : '레포 등록에 실패했습니다.'),
      },
    )
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface-raised shadow-sm">
      <div className="flex items-center gap-4 px-5 py-4">
        <span
          aria-hidden
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-fg"
        >
          <GithubIcon />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-body font-semibold text-fg-strong">
            GitHub에서 레포 가져오기
          </p>
          <p className="mt-0.5 text-caption text-fg-muted">
            연결된 GitHub 계정의 레포지토리를 등록할 수 있습니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="shrink-0 rounded-lg bg-primary px-4 py-2 text-button text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
        >
          {open ? '닫기' : '레포 가져오기'}
        </button>
      </div>

      {open ? (
        <div className="border-t border-border px-5 py-4">
          {error ? (
            <p className="mb-3 rounded-lg bg-danger-50 px-3 py-2 text-caption text-danger-700">
              {error}
            </p>
          ) : null}

          {candidates.isPending ? (
            <p className="py-6 text-center text-body text-fg-muted">
              불러오는 중…
            </p>
          ) : candidates.isError ? (
            <p className="py-6 text-center text-body text-danger-700">
              {isApiError(candidates.error)
                ? candidates.error.message
                : 'GitHub 레포를 불러오지 못했습니다.'}
            </p>
          ) : candidates.data.length === 0 ? (
            <p className="py-6 text-center text-body text-fg-muted">
              표시할 레포가 없습니다.
            </p>
          ) : (
            <ul className="-mx-2 flex max-h-80 flex-col overflow-y-auto">
              {candidates.data.map((repo) => (
                <li
                  key={repo.githubRepoId}
                  className="flex items-center gap-3 rounded-lg px-2 py-2 transition-colors duration-fast hover:bg-surface"
                >
                  <span aria-hidden className="shrink-0 text-fg-subtle">
                    <RepoIcon />
                  </span>
                  <span className="flex min-w-0 flex-1 items-center gap-2">
                    <span className="truncate text-body text-fg-strong">
                      {repo.fullName}
                    </span>
                    {repo.isPrivate ? (
                      <span className="inline-flex shrink-0 items-center gap-1 rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
                        <LockIcon />
                        private
                      </span>
                    ) : null}
                  </span>
                  <button
                    type="button"
                    disabled={repo.alreadyRegistered || register.isPending}
                    onClick={() => onRegister(repo)}
                    className="shrink-0 rounded-lg border border-border-strong px-3 py-1.5 text-button text-fg-strong transition-colors duration-fast hover:border-primary hover:text-primary-fg disabled:cursor-default disabled:border-border disabled:bg-surface disabled:text-fg-subtle"
                  >
                    {repo.alreadyRegistered ? '등록됨' : '등록'}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="mt-3 flex items-center justify-between border-t border-border pt-3">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="rounded-md px-2 py-1 text-caption text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong disabled:opacity-40 disabled:hover:bg-transparent"
            >
              ← 이전
            </button>
            <span className="text-caption text-fg-subtle">{page} 페이지</span>
            <button
              type="button"
              disabled={(candidates.data?.length ?? 0) < PER_PAGE}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md px-2 py-1 text-caption text-fg-muted transition-colors duration-fast hover:bg-surface hover:text-fg-strong disabled:opacity-40 disabled:hover:bg-transparent"
            >
              다음 →
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function GithubIcon() {
  return (
    <svg viewBox="0 0 20 20" width="22" height="22" fill="currentColor">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M10 1.5a8.5 8.5 0 0 0-2.69 16.57c.43.08.58-.18.58-.41v-1.45c-2.36.51-2.86-1.14-2.86-1.14-.39-.98-.94-1.24-.94-1.24-.77-.53.06-.52.06-.52.85.06 1.3.88 1.3.88.76 1.3 1.98.92 2.46.7.08-.55.3-.92.54-1.13-1.88-.21-3.86-.94-3.86-4.19 0-.92.33-1.68.87-2.27-.09-.21-.38-1.07.08-2.24 0 0 .71-.23 2.33.87a8.1 8.1 0 0 1 4.24 0c1.61-1.1 2.32-.87 2.32-.87.46 1.17.17 2.03.08 2.24.54.59.87 1.35.87 2.27 0 3.26-1.98 3.98-3.87 4.19.3.27.58.79.58 1.6v2.37c0 .23.15.5.59.41A8.5 8.5 0 0 0 10 1.5Z"
      />
    </svg>
  )
}

function RepoIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
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

function LockIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="11"
      height="11"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="4.5" y="8.5" width="11" height="8" rx="1.5" />
      <path d="M7 8.5V6a3 3 0 0 1 6 0v2.5" />
    </svg>
  )
}
