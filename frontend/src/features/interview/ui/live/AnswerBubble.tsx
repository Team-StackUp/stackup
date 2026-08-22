import { memo } from 'react'
import { isTranscribing } from '@/domain/session'
import type { Message } from '@/domain/session'
import { useMessageAudio } from '../../lib/media/useMessageAudio'

export const AnswerBubble = memo(function AnswerBubble({ message }: { message: Message }) {
  const transcribing = isTranscribing(message)
  const failed = transcribing && message.status === 'FAILED'
  const hasVoice = Boolean(message.audioFilePath)

  const { url, load } = useMessageAudio(message.sessionId, message.id)

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="max-w-[80%] whitespace-pre-wrap rounded-xl rounded-tr-sm bg-primary px-4 py-3 text-body text-fg-on-primary">
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
      {hasVoice &&
        (url ? (
          <audio controls src={url} autoPlay className="h-9 max-w-[80%]" />
        ) : (
          <button
            type="button"
            onClick={() => void load()}
            className="rounded-pill px-2.5 py-1 text-caption text-fg-muted transition-colors hover:bg-surface hover:text-fg"
          >
            ▶ 내 답변 듣기
          </button>
        ))}
    </div>
  )
})
