import { useState } from 'react'
import type { KeyboardEvent } from 'react'
import { TextArea } from '@/shared/ui/TextArea'
import { Button } from '@/shared/ui/Button'

export function AnswerComposer({
  disabled = false,
  onSubmit,
}: {
  disabled?: boolean
  onSubmit: (content: string) => void
}) {
  const [value, setValue] = useState('')

  const submit = () => {
    const trimmed = value.trim()
    if (!trimmed || disabled) return
    onSubmit(trimmed)
    setValue('')
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="flex items-end gap-2 border-t border-border bg-surface-raised px-4 py-3">
      <TextArea
        value={value}
        onChange={setValue}
        onKeyDown={onKeyDown}
        disabled={disabled}
        maxLength={8000}
        aria-label="답변 입력"
        placeholder={disabled ? '질문을 기다리는 중…' : '답변을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)'}
      />
      <Button onClick={submit} disabled={disabled || value.trim().length === 0}>
        전송
      </Button>
    </div>
  )
}
