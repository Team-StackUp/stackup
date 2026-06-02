import { useEffect, useRef } from 'react'
import { isQuestion } from '@/domain/session'
import type { ThreadItem } from '../../model/useLiveInterview'
import { QuestionBubble } from './QuestionBubble'
import { AnswerBubble } from './AnswerBubble'
import { TypingIndicator } from './TypingIndicator'

export function ConversationThread({
  items,
  awaitingQuestion,
}: {
  items: ThreadItem[]
  awaitingQuestion: boolean
}) {
  const bottomRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [items.length, awaitingQuestion])

  return (
    <div className="flex h-full flex-col gap-3 overflow-y-auto px-4 py-6">
      {items.map((item) =>
        isQuestion(item) ? (
          <QuestionBubble key={item.key} message={item} />
        ) : (
          <AnswerBubble key={item.key} message={item} />
        ),
      )}
      {awaitingQuestion ? <TypingIndicator /> : null}
      <div ref={bottomRef} />
    </div>
  )
}
