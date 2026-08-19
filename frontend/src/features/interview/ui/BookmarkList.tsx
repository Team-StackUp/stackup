import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState, ListSkeleton, QueryError, StatusBadge } from '@/shared/ui'
import { Button } from '@/shared/ui/Button'
import { categoryLabel } from '../lib/categoryLabel'
import { useBookmarks, useSetQuestionBookmark } from '../model/useBookmarks'
import type { BookmarkedQuestion } from '../api/bookmarkApi'
import { BookmarkDrill } from './BookmarkDrill'

export function BookmarkList() {
  const { data = [], isPending, isError, refetch } = useBookmarks()
  const [drilling, setDrilling] = useState(false)

  if (isPending) {
    return <ListSkeleton label="오답노트를 불러오는 중…" />
  }
  if (isError) {
    return (
      <QueryError message="오답노트를 불러오지 못했습니다." onRetry={() => refetch()} />
    )
  }
  if (data.length === 0) {
    return (
      <EmptyState
        title="아직 담아둔 질문이 없어요"
        description="끝난 면접의 질문·답변 기록에서 별을 누르면 여기에 모입니다."
      />
    )
  }

  if (drilling) {
    return <BookmarkDrill items={data} onExit={() => setDrilling(false)} />
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-caption text-fg-subtle">담아둔 질문 {data.length}개</p>
        <Button size="sm" onClick={() => setDrilling(true)}>
          복습 시작
        </Button>
      </div>
      <ul className="flex flex-col gap-3">
        {data.map((item) => (
          <BookmarkCard key={item.messageId} item={item} />
        ))}
      </ul>
    </div>
  )
}

function BookmarkCard({ item }: { item: BookmarkedQuestion }) {
  // 모범 답안은 접어 둔다 — 먼저 스스로 떠올려 보고 펼치는 게 복습의 핵심이다.
  const [open, setOpen] = useState(false)
  const remove = useSetQuestionBookmark(item.sessionId ?? 0)
  const label = categoryLabel(item.category)
  const hasReview = Boolean(item.myAnswer || item.modelAnswer || item.coachingComment)

  return (
    <li className="rounded-xl border border-border bg-surface-raised p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {label && <StatusBadge tone="info">{label}</StatusBadge>}
            {item.sessionId != null && (
              <Link
                to={`/sessions/${item.sessionId}`}
                className="truncate text-caption text-fg-subtle underline decoration-border-strong underline-offset-2 hover:decoration-primary"
              >
                {item.sessionTitle || `면접 #${item.sessionId}`}
              </Link>
            )}
          </div>
          <p
            className="mt-2 text-body font-semibold text-fg-strong"
            style={{ wordBreak: 'keep-all' }}
          >
            {item.question}
          </p>
          {item.expectedSignal && (
            <p className="mt-1.5 text-caption text-fg-muted">
              평가 관점: {item.expectedSignal}
            </p>
          )}
        </div>
        <button
          type="button"
          disabled={remove.isPending}
          aria-label="오답노트에서 빼기"
          onClick={() =>
            item.messageId != null &&
            remove.mutate({ messageId: item.messageId, bookmarked: false })
          }
          className="shrink-0 rounded-md p-1 text-fg-subtle transition-colors duration-fast hover:bg-surface hover:text-danger-700 disabled:opacity-40"
        >
          <TrashIcon />
        </button>
      </div>

      {hasReview ? (
        <div className="mt-3 border-t border-border pt-3">
          <button
            type="button"
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
            className="text-caption font-medium text-primary-fg hover:underline"
          >
            {open ? '복습 내용 접기' : '내 답변 · 모범 답안 보기'}
          </button>
          {open && (
            <div className="mt-3 flex flex-col gap-3">
              {item.myAnswer && (
                <Block title="그때 내 답변" body={item.myAnswer} tone="muted" />
              )}
              {item.modelAnswer && <Block title="모범 답안" body={item.modelAnswer} />}
              {item.coachingComment && (
                <Block title="코칭" body={item.coachingComment} tone="muted" />
              )}
            </div>
          )}
        </div>
      ) : (
        // 답변 전에 담았거나 피드백이 아직 없으면 복습 재료가 없다 — 이유를 알려준다.
        <p className="mt-3 border-t border-border pt-3 text-caption text-fg-subtle">
          이 질문에는 아직 답변·피드백 기록이 없어요.
        </p>
      )}
    </li>
  )
}

function Block({
  title,
  body,
  tone = 'strong',
}: {
  title: string
  body: string
  tone?: 'strong' | 'muted'
}) {
  return (
    <div className="rounded-lg border border-border bg-surface px-3 py-2.5">
      <p className="text-caption font-medium text-fg-subtle">{title}</p>
      <p
        className={[
          'mt-1 whitespace-pre-wrap text-body font-normal leading-relaxed',
          tone === 'strong' ? 'text-fg-strong' : 'text-fg-muted',
        ].join(' ')}
      >
        {body}
      </p>
    </div>
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
      aria-hidden
    >
      <path d="M4 6h12M8 6V4.5A1 1 0 0 1 9 3.5h2a1 1 0 0 1 1 1V6m1.5 0-.5 9a1.5 1.5 0 0 1-1.5 1.4H7.5A1.5 1.5 0 0 1 6 15l-.5-9" />
    </svg>
  )
}
