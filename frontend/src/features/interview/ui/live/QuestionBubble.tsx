import type { Message } from '@/domain/session'
import { StatusBadge } from '@/shared/ui/StatusBadge'

const CATEGORY_LABEL: Record<string, string> = {
  CS_FUNDAMENTAL: 'CS 기초',
  PROJECT_DEEP_DIVE: '프로젝트 심화',
  TECH_CHOICE: '기술 선택',
  BEHAVIORAL: '인성·행동',
}

export function QuestionBubble({ message }: { message: Message }) {
  const categoryLabel = message.category
    ? (CATEGORY_LABEL[message.category] ?? message.category)
    : null
  const hasMeta = Boolean(categoryLabel || message.targetEvidence)

  return (
    <div className="flex justify-start">
      <div className="flex max-w-[80%] flex-col gap-1.5">
        {hasMeta && (
          <div className="flex flex-wrap items-center gap-2">
            {categoryLabel && <StatusBadge tone="info">{categoryLabel}</StatusBadge>}
            {message.targetEvidence && (
              <span className="text-caption text-fg-muted">
                근거: {message.targetEvidence}
              </span>
            )}
          </div>
        )}
        <div className="whitespace-pre-wrap rounded-lg rounded-tl-sm bg-surface-raised px-4 py-3 text-body text-fg shadow-sm">
          {message.content}
        </div>
      </div>
    </div>
  )
}
