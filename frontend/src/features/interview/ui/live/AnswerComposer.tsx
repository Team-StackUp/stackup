import { useState } from 'react'
import type { KeyboardEvent } from 'react'
import { TextArea } from '@/shared/ui/TextArea'
import { Button } from '@/shared/ui/Button'
import { useVoiceRecorder } from '../../lib/media/useVoiceRecorder'

function MicIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path
        d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
      <path
        d="M5 11a7 7 0 0 0 14 0M12 18v3"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function AnswerComposer({
  disabled = false,
  submitLocked = false,
  onSubmit,
  onSubmitVoice,
  voiceUploading = false,
}: {
  disabled?: boolean
  submitLocked?: boolean
  onSubmit: (content: string) => void
  onSubmitVoice?: (audio: Blob) => void
  voiceUploading?: boolean
}) {
  const [value, setValue] = useState('')
  const { status: recStatus, start, stop, cancel } = useVoiceRecorder()
  const recording = recStatus === 'recording' || recStatus === 'requesting'
  const voiceSupported = recStatus !== 'unsupported' && Boolean(onSubmitVoice)

  const submit = () => {
    const trimmed = value.trim()
    if (!trimmed || disabled || submitLocked) return
    onSubmit(trimmed)
    setValue('')
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  const finishRecording = async () => {
    const blob = await stop()
    if (blob && onSubmitVoice) onSubmitVoice(blob)
  }

  // 녹음/업로드 중에는 전용 바를 보여준다.
  if (recording || voiceUploading) {
    return (
      <div className="flex items-center justify-between gap-3 border-t border-border bg-surface-raised px-4 py-3">
        <span className="flex items-center gap-2 text-body text-fg">
          {voiceUploading ? (
            '음성 답변 업로드 중…'
          ) : (
            <>
              <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-danger" />
              녹음 중… 답변이 끝나면 전송을 누르세요
            </>
          )}
        </span>
        {!voiceUploading && (
          <div className="flex items-center gap-2">
            <Button variant="ghost" onClick={cancel}>
              취소
            </Button>
            <Button onClick={finishRecording}>전송</Button>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1 border-t border-border bg-surface-raised px-4 py-3">
      <div className="flex items-end gap-2">
        <TextArea
          value={value}
          onChange={setValue}
          onKeyDown={onKeyDown}
          disabled={disabled}
          maxLength={8000}
          aria-label="답변 입력"
          placeholder={
            disabled
              ? '질문을 기다리는 중…'
              : submitLocked
                ? '질문이 끝나면 전송할 수 있어요'
                : '답변을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)'
          }
        />
        {voiceSupported && (
          <Button
            variant="secondary"
            onClick={start}
            disabled={disabled || submitLocked}
            aria-label="음성으로 답변"
            title="음성으로 답변"
          >
            <MicIcon />
          </Button>
        )}
        <Button onClick={submit} disabled={disabled || submitLocked || value.trim().length === 0}>
          전송
        </Button>
      </div>
      {recStatus === 'denied' && (
        <span className="text-caption text-fg-muted">
          마이크 권한이 거부되어 텍스트로만 답변할 수 있어요.
        </span>
      )}
    </div>
  )
}
