import { memo, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { TextArea } from '@/shared/ui/TextArea'
import { Button } from '@/shared/ui/Button'
import { useVoiceRecorder } from '../../lib/media/useVoiceRecorder'
import { MicLevelMeter } from './MicLevelMeter'

function formatElapsed(sec: number): string {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

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

export const AnswerComposer = memo(function AnswerComposer({
  disabled = false,
  disabledReason = 'awaiting-question',
  submitLocked = false,
  onSubmit,
  restoreDraft,
  onSubmitVoice,
  voiceUploading = false,
}: {
  disabled?: boolean
  /** 입력이 막힌 이유 — 안내 문구가 달라진다. 연결 끊김을 '질문 대기'로 적으면 오도한다. */
  disabledReason?: 'awaiting-question' | 'disconnected'
  submitLocked?: boolean
  onSubmit: (content: string) => void
  restoreDraft?: { content: string; nonce: number } | null
  onSubmitVoice?: (audio: Blob) => void
  voiceUploading?: boolean
}) {
  const [value, setValue] = useState('')

  // 전송 실패로 롤백된 답변을 입력창에 복원(입력 중인 새 내용은 덮어쓰지 않음).
  // 프롭 변화에 따른 상태 보정은 effect 대신 렌더 중 직접 처리(React 권장 패턴).
  const [restoredNonce, setRestoredNonce] = useState<number | undefined>(undefined)
  if (restoreDraft && restoreDraft.nonce !== restoredNonce) {
    setRestoredNonce(restoreDraft.nonce)
    if (value.trim().length === 0 && restoreDraft.content) setValue(restoreDraft.content)
  }
  const { status: recStatus, stream, start, stop, cancel } = useVoiceRecorder()
  const recording = recStatus === 'recording' || recStatus === 'requesting'
  const voiceSupported = recStatus !== 'unsupported' && Boolean(onSubmitVoice)

  // 녹음 경과 시간(초). recording 상태에서만 흐른다. 리셋은 시작 시점(이벤트)에서.
  const [elapsed, setElapsed] = useState(0)
  useEffect(() => {
    if (recStatus !== 'recording') return
    const startedAt = Date.now()
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - startedAt) / 1000)), 250)
    return () => clearInterval(id)
  }, [recStatus])

  const beginRecording = () => {
    setElapsed(0)
    void start()
  }

  // 녹음/업로드 전용 바에서 입력 바로 돌아오면 키보드 사용자를 위해 답변창에 포커스를 돌려준다.
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const busy = recording || voiceUploading
  const prevBusy = useRef(false)
  useEffect(() => {
    if (prevBusy.current && !busy && !disabled) {
      textareaRef.current?.focus()
    }
    prevBusy.current = busy
  }, [busy, disabled])

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
    const requesting = recStatus === 'requesting'
    return (
      <div className="flex items-center justify-between gap-3 border-t border-border bg-surface-raised px-4 py-3">
        {voiceUploading ? (
          <span className="text-body text-fg">음성 답변 업로드 중…</span>
        ) : requesting ? (
          <span className="text-body text-fg-muted">
            마이크 권한을 기다리는 중… 브라우저 알림에서 허용해 주세요.
          </span>
        ) : (
          <span className="flex min-w-0 items-center gap-3 text-body text-fg">
            <span className="flex items-center gap-2">
              <span className="inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-danger" />
              <span className="font-medium tabular-nums" aria-label={`녹음 중 ${formatElapsed(elapsed)}`}>
                {formatElapsed(elapsed)}
              </span>
            </span>
            <MicLevelMeter stream={stream} />
            <span className="hidden truncate text-caption text-fg-muted sm:inline">
              답변이 끝나면 전송을 누르세요
            </span>
          </span>
        )}
        {/* 권한 대기 중에도 나갈 수 있어야 한다 — 프롬프트를 놓치면 getUserMedia 가 영원히
            응답하지 않고, 그동안 텍스트 입력창이 사라져 답변 자체가 막힌다. */}
        {!voiceUploading && (
          <div className="flex shrink-0 items-center gap-2">
            <Button variant="ghost" onClick={cancel}>
              {requesting ? '텍스트로 답변' : '취소'}
            </Button>
            {!requesting && <Button onClick={finishRecording}>전송</Button>}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1 border-t border-border bg-surface-raised px-4 py-3">
      <div className="flex items-end gap-2">
        <TextArea
          ref={textareaRef}
          value={value}
          onChange={setValue}
          onKeyDown={onKeyDown}
          disabled={disabled}
          maxLength={8000}
          aria-label="답변 입력"
          placeholder={
            disabled
              ? disabledReason === 'disconnected'
                ? '연결이 끊겨 지금은 보낼 수 없어요. 재연결되면 이어서 답변할 수 있습니다.'
                : '질문을 기다리는 중…'
              : submitLocked
                ? '질문이 끝나면 전송할 수 있어요'
                : '답변을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)'
          }
        />
        {voiceSupported && (
          <Button
            variant="secondary"
            onClick={beginRecording}
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
})
