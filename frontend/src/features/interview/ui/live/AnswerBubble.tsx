import { isTranscribing } from '@/domain/session'
import type { Message } from '@/domain/session'

export function AnswerBubble({ message }: { message: Message }) {
  const transcribing = isTranscribing(message)
  const failed = transcribing && message.status === 'FAILED'

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="max-w-[80%] whitespace-pre-wrap rounded-lg rounded-tr-sm bg-primary px-4 py-3 text-body text-fg-on-primary shadow-sm">
        {!transcribing ? (
          message.content
        ) : (
          <span className="inline-flex items-center gap-2 text-fg-on-primary/80">
            {failed ? (
              '음성 인식에 실패했어요. 다시 답변해 주세요.'
            ) : (
              <>
                <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-fg-on-primary" />
                음성 인식 중…
              </>
            )}
          </span>
        )}
      </div>
      {message.audioFileUrl && (
        <audio controls src={message.audioFileUrl} className="max-w-[80%]" preload="none" />
      )}
    </div>
  )
}
