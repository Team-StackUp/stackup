import { isQuestion } from '@/domain/session'
import { QueryError } from '@/shared/ui'
import { Spinner } from '@/shared/ui/Spinner'
import { useSessionMessages } from '../model/useSessionMessages'
import { QuestionBubble } from './live/QuestionBubble'
import { AnswerBubble } from './live/AnswerBubble'
import { AnswerCoachingAccordion } from './live/AnswerCoachingAccordion'

// 종료된 세션의 질문/답변 원문(읽기 전용). 라이브 버블을 재사용해
// 음성 질문 재생(TTS)·음성 답변 재생도 그대로 노출한다.
export function InterviewTranscript({ sessionId }: { sessionId: number }) {
  const { data, isLoading, isError, refetch } = useSessionMessages(sessionId)

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <Spinner />
      </div>
    )
  }

  // 에러를 empty(null)로 흘리면 "질문 & 답변 섹션이 사라졌다"로 보인다 — 명시적으로 알린다.
  if (isError) {
    return (
      <section className="flex flex-col gap-4">
        <h2 className="font-sans text-[18px] font-bold tracking-[-0.02em] text-fg">질문 &amp; 답변</h2>
        <QueryError message="질문·답변 기록을 불러오지 못했습니다." onRetry={() => refetch()} />
      </section>
    )
  }

  const items = [...(data ?? [])].sort(
    (a, b) => (a.sequenceNumber ?? 0) - (b.sequenceNumber ?? 0),
  )
  if (items.length === 0) return null

  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-sans text-[18px] font-bold tracking-[-0.02em] text-fg">질문 &amp; 답변</h2>
      <div className="flex flex-col gap-3">
        {items.map((m) =>
          isQuestion(m) ? (
            <QuestionBubble key={m.id} message={m} bookmarkable />
          ) : (
            <div key={m.id} className="flex flex-col items-end gap-1">
              <AnswerBubble message={m} />
              <AnswerCoachingAccordion message={m} />
            </div>
          ),
        )}
      </div>
    </section>
  )
}
