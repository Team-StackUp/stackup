import { memo } from 'react'
import { isTranscribing } from '@/domain/session'
import type { Message } from '@/domain/session'
import { useMessageAudio } from '../../lib/media/useMessageAudio'

export const AnswerBubble = memo(function AnswerBubble({
  message,
  onRetranscribe,
  retranscribing = false,
}: {
  message: Message
  // STT 실패한 마지막 답변에만 주어진다 — 이미 다시 답변한 뒤라면 서버가 거절하므로
  // (VoiceRetranscribeService) 버튼 자체를 내지 않는다.
  onRetranscribe?: () => void
  retranscribing?: boolean
}) {
  const transcribing = isTranscribing(message)
  const failed = transcribing && message.status === 'FAILED'
  const hasVoice = Boolean(message.audioFilePath)
  const canRetranscribe = failed && hasVoice && Boolean(onRetranscribe)

  const { url, load } = useMessageAudio(message.sessionId, message.id)

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="max-w-[80%] whitespace-pre-wrap rounded-xl rounded-tr-sm bg-primary px-4 py-3 text-body text-fg-on-primary">
        {!transcribing ? (
          message.content
        ) : (
          <span className="inline-flex items-center gap-2 text-fg-on-primary/80">
            {failed ? (
              // 녹음은 그대로 있으니 "다시 답변" 이 유일한 길인 것처럼 말하지 않는다.
              canRetranscribe
                ? '음성 인식에 실패했어요. 녹음은 남아 있어요.'
                : '음성 인식에 실패했어요. 다시 답변해 주세요.'
            ) : (
              <>
                <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-fg-on-primary" />
                음성 인식 중…
              </>
            )}
          </span>
        )}
      </div>
      <div className="flex items-center gap-1">
        {canRetranscribe ? (
          <button
            type="button"
            onClick={onRetranscribe}
            disabled={retranscribing}
            className="rounded-pill px-2.5 py-1 text-caption font-medium text-primary transition-colors hover:bg-surface disabled:cursor-not-allowed disabled:text-fg-muted"
          >
            {retranscribing ? '다시 인식하는 중…' : '↻ 다시 인식하기'}
          </button>
        ) : null}
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
    </div>
  )
})
