import type { ReactNode } from 'react'
import { Markdown } from '@/shared/ui'

// 오답노트 목록/드릴이 공유하는 복습 패널 — 제목 스타일만 호출부가 넘긴다.
// 모범 답안(GFM 마크다운 계약)만 markdown 으로 렌더하고, 사용자 입력(내 답변)·plain 계약
// 필드(코칭)는 절대 마크다운으로 렌더하지 않는다 (docs/frontend-types.md §6.5).
export function ReviewBlock({
  label,
  body,
  tone = 'strong',
  markdown = false,
}: {
  label: ReactNode
  body: string
  tone?: 'strong' | 'muted'
  markdown?: boolean
}) {
  const toneClass = tone === 'strong' ? 'text-fg-strong' : 'text-fg-muted'
  return (
    <div className="rounded-lg border border-border bg-surface px-3 py-2.5">
      {label}
      {markdown ? (
        <div className="mt-1">
          <Markdown className={toneClass}>{body}</Markdown>
        </div>
      ) : (
        <p
          className={[
            'mt-1 whitespace-pre-wrap text-body font-normal leading-relaxed',
            toneClass,
          ].join(' ')}
        >
          {body}
        </p>
      )}
    </div>
  )
}
