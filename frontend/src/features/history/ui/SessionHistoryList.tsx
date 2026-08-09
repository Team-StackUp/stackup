import { Link } from 'react-router-dom'
import { EmptyState, ListSkeleton } from '@/shared/ui'
import { useSessions } from '../model/useHistory'
import { SessionCard } from './SessionCard'

export function SessionHistoryList() {
  const {
    data,
    isLoading,
    isError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useSessions()

  if (isLoading) {
    return <ListSkeleton count={4} label="면접 기록을 불러오는 중…" className="py-2" />
  }
  if (isError) {
    return (
      <div className="flex flex-col items-center gap-2 py-8">
        <p className="text-body text-fg-muted">세션을 불러오지 못했습니다.</p>
        <button className="text-caption text-primary-fg underline" onClick={() => refetch()}>
          다시 시도
        </button>
      </div>
    )
  }

  const sessions = data?.pages.flatMap((p) => p.content ?? []) ?? []
  if (sessions.length === 0) {
    return (
      <EmptyState
        title="아직 진행한 면접이 없어요"
        description="첫 모의면접을 시작해 피드백을 받아보세요."
        action={
          <Link
            to="/sessions/new"
            className="inline-flex items-center rounded-md bg-primary px-4 py-2 text-button font-medium text-fg-on-primary transition-colors hover:bg-primary-hover"
          >
            면접 시작
          </Link>
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {sessions.map((s) => (
        <SessionCard key={s.id} session={s} />
      ))}
      {hasNextPage && (
        <button
          className="mt-2 self-center text-caption text-fg-muted underline"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
        >
          {isFetchingNextPage ? '불러오는 중…' : '더 보기'}
        </button>
      )}
    </div>
  )
}
