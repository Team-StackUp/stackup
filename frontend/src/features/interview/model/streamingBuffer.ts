import { FOLLOWUP_GENERATING_TEXT } from '@/domain/session'
export { FOLLOWUP_GENERATING_TEXT }

export type DeltaPayload = { messageId: number; seq: number; text: string }

// messageId -> (seq -> 조각). 발행측(AI followup consumer)은 seq 를 0부터 1씩 증가시키지만,
// WS 경로는 프레임 순서를 보장하지 않으므로 도착 순서가 아닌 seq 로 재조립한다.
export type DeltaBuffer = Record<number, Record<number, string>>

// 중복 seq(재전달)는 멱등 — 첫 조각을 유지한다.
export function applyDelta(buffer: DeltaBuffer, delta: DeltaPayload): DeltaBuffer {
  const parts = buffer[delta.messageId]
  if (parts?.[delta.seq] !== undefined) return buffer
  return { ...buffer, [delta.messageId]: { ...parts, [delta.seq]: delta.text } }
}

// seq 0부터 연속으로 이어진 구간만 join 한다. 갭 뒤에 도착한 조각은 갭이 채워질 때까지
// 표시하지 않는다 — 프레임이 정말 유실되면 종료 시 콜백 정본(reconcile)이 덮는다.
// seq 0 이 아직 없으면 undefined — 호출부가 placeholder 표시를 유지하게 한다.
export function bufferedText(
  buffer: DeltaBuffer,
  messageId: number | undefined,
): string | undefined {
  const parts = buffer[messageId ?? -1]
  if (!parts || parts[0] === undefined) return undefined
  let text = ''
  for (let seq = 0; parts[seq] !== undefined; seq++) text += parts[seq]
  return text
}

export function isStreamingMessage(
  message: { content?: string | null },
  bufferedText: string | undefined,
): boolean {
  if (bufferedText !== undefined) return true
  return message.content === FOLLOWUP_GENERATING_TEXT
}
