import type { Message } from '@/domain/session'

export function AnswerBubble({ message }: { message: Message }) {
  return (
    <div className="flex justify-end">
      <div className="max-w-[80%] whitespace-pre-wrap rounded-lg rounded-tr-sm bg-primary px-4 py-3 text-body text-fg-on-primary shadow-sm">
        {message.content}
      </div>
    </div>
  )
}
