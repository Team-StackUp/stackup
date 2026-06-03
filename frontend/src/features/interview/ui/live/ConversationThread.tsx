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
  // 내부 스레드 컨테이너만 스크롤한다. scrollIntoView 는 스크롤 가능한 모든
  // 조상(=window)까지 스크롤해 페이지가 푸터로 끌려 내려가므로 사용하지 않는다.
  const containerRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = containerRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [items.length, awaitingQuestion])

  // 가장 마지막 질문만 자동재생(초기 로드 시 과거 질문 일괄 재생 방지).
  const lastQuestionKey = [...items].reverse().find(isQuestion)?.key

  return (
    <div ref={containerRef} className="flex h-full flex-col gap-3 overflow-y-auto px-4 py-6">
      {items.map((item) =>
        isQuestion(item) ? (
          <QuestionBubble key={item.key} message={item} autoPlay={item.key === lastQuestionKey} />
        ) : (
          <AnswerBubble key={item.key} message={item} />
        ),
      )}
      {awaitingQuestion ? <TypingIndicator /> : null}
    </div>
  )
}
