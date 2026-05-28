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
    <div className="rounded-xl border border-border bg-surface">
      <div className="flex items-center justify-between gap-4 px-4 py-3">
        <p className="text-body text-fg-strong">GitHub에서 레포 가져오기</p>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="rounded-lg bg-primary px-4 py-2 text-button text-fg-on-primary transition-colors hover:bg-primary-hover"
        >
          {open ? '닫기' : '레포 가져오기'}
        </button>
      </div>

      {open ? (
        <div className="border-t border-border px-4 py-3">
          {error ? (
            <p className="text-caption text-danger-700 mb-2">{error}</p>
          ) : null}

          {candidates.isPending ? (
            <p className="text-body text-fg-muted">불러오는 중…</p>
          ) : candidates.isError ? (
            <p className="text-body text-danger-700">
              {isApiError(candidates.error)
                ? candidates.error.message
                : 'GitHub 레포를 불러오지 못했습니다.'}
            </p>
          ) : candidates.data.length === 0 ? (
            <p className="text-body text-fg-muted">표시할 레포가 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {candidates.data.map((repo) => (
                <li
                  key={repo.githubRepoId}
                  className="flex items-center justify-between gap-4 rounded-lg bg-surface-raised px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="text-body text-fg-strong truncate">
                      {repo.fullName}
                      {repo.isPrivate ? (
                        <span className="text-caption text-fg-subtle ml-2">
                          private
                        </span>
                      ) : null}
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={repo.alreadyRegistered || register.isPending}
                    onClick={() => onRegister(repo)}
                    className="shrink-0 rounded-lg border border-border-strong px-3 py-1.5 text-button text-fg-strong transition-colors hover:bg-surface disabled:opacity-50"
                  >
                    {repo.alreadyRegistered ? '등록됨' : '등록'}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="flex items-center justify-between mt-3">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="text-caption text-fg-muted disabled:opacity-40"
            >
              이전
            </button>
            <span className="text-caption text-fg-subtle">{page}</span>
            <button
              type="button"
              disabled={(candidates.data?.length ?? 0) < PER_PAGE}
              onClick={() => setPage((p) => p + 1)}
              className="text-caption text-fg-muted disabled:opacity-40"
            >
              다음
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
