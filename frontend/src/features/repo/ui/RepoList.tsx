import { isApiError } from '@/shared/api'
import { StatusBadge, type StatusTone } from '@/shared/ui'
import {
  useDeleteRepository,
  useRegisteredRepositories,
} from '../model/useRepositories'
import type { RepositoryStatus } from '../model/types'

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
    return <p className="text-body text-fg-muted">레포지토리를 불러오는 중…</p>
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
      <div className="rounded-xl border border-dashed border-border-strong bg-surface p-10 text-center">
        <p className="text-body text-fg-muted">아직 등록된 레포지토리가 없습니다.</p>
        <p className="text-caption text-fg-subtle mt-2">
          위에서 GitHub 레포를 가져오면 분석이 자동으로 시작됩니다.
        </p>
      </div>
    )
  }

  return (
    <ul className="flex flex-col gap-2">
      {data.map((repo) => {
        const meta = STATUS_META[repo.status]
        return (
          <li
            key={repo.id}
            className="flex items-center justify-between gap-4 rounded-xl border border-border bg-surface-raised px-4 py-3"
          >
            <div className="min-w-0">
              <a
                href={repo.repoUrl}
                target="_blank"
                rel="noreferrer"
                className="text-body text-fg-strong truncate hover:underline"
              >
                {repo.repoFullName}
              </a>
              <p className="text-caption text-fg-subtle mt-0.5">
                {repo.defaultBranch ?? 'main'}
              </p>
            </div>
            <div className="flex items-center gap-3 shrink-0">
              <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
              <button
                type="button"
                disabled={remove.isPending}
                onClick={() => remove.mutate(repo.id)}
                className="text-caption text-fg-muted hover:text-danger-700 disabled:opacity-60"
              >
                삭제
              </button>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
