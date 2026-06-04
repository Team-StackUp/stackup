import { FOLLOWUP_GENERATING_TEXT } from '@/domain/session'
export { FOLLOWUP_GENERATING_TEXT }

export type DeltaPayload = { messageId: number; seq: number; text: string }

// messageId -> 누적 텍스트. seq 는 순서 보조(현재는 단순 append, gap 은 종료 reconcile 로 자기치유).
export function applyDelta(
  buffer: Record<number, string>,
  delta: DeltaPayload,
): Record<number, string> {
  const prev = buffer[delta.messageId] ?? ''
  return { ...buffer, [delta.messageId]: prev + delta.text }
}

export function isStreamingMessage(
  message: { content?: string | null },
  bufferedText: string | undefined,
): boolean {
  if (bufferedText !== undefined) return true
  return message.content === FOLLOWUP_GENERATING_TEXT
}
