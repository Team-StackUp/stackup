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
    return <p className="py-8 text-center text-body text-fg-muted">불러오는 중…</p>
  }
  if (isError) {
    return (
      <div className="flex flex-col items-center gap-2 py-8">
        <p className="text-body text-fg-muted">세션을 불러오지 못했습니다.</p>
        <button className="text-caption text-primary underline" onClick={() => refetch()}>
          다시 시도
        </button>
      </div>
    )
  }

  const sessions = data?.pages.flatMap((p) => p.content ?? []) ?? []
  if (sessions.length === 0) {
    return (
      <p className="py-8 text-center text-body text-fg-muted">아직 진행한 면접이 없어요.</p>
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
