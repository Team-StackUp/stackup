import type { Message } from '@/domain/session'

export function QuestionBubble({ message }: { message: Message }) {
  return (
    <div className="flex justify-start">
      <div className="max-w-[80%] whitespace-pre-wrap rounded-lg rounded-tl-sm bg-surface-raised px-4 py-3 text-body text-fg shadow-sm">
        {message.content}
      </div>
    </div>
  )
}
