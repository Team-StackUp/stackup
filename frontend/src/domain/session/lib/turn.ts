import type { Message, Session } from '../model/types'

export type Turn = 'AWAITING_ANSWER' | 'WAITING_FOR_QUESTION'

export function isQuestion(message: Message): boolean {
  return message.role === 'INTERVIEWER'
}

export function isAnswer(message: Message): boolean {
  return message.role === 'INTERVIEWEE'
}

// 음성 답변 placeholder 의 임시 content (백엔드 InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT 와 동일).
// STT 완료 전까지 이 값이 들어 있고, 완료되면 실제 transcript 로 교체된다.
export const VOICE_TRANSCRIBING_TEXT = '(transcribing)'

// 꼬리질문 생성 중인 placeholder content (백엔드 sentinel 과 동일).
// 스트리밍이 완료되면 실제 질문 텍스트로 교체된다.
export const FOLLOWUP_GENERATING_TEXT = '(생성 중)'

// STT 대기 중(transcript 아직 없음)인 음성 답변인지.
export function isTranscribing(message: Message): boolean {
  if (!isAnswer(message)) return false
  const c = (message.content ?? '').trim()
  return c === '' || c === VOICE_TRANSCRIBING_TEXT
}

export function currentTurn(messages: Message[]): Turn {
  const last = messages[messages.length - 1]
  // 마지막이 '내용이 있는' 면접관 질문일 때만 답변 차례. 질문 생성 중(빈 content 또는 sentinel)
  // 메시지가 먼저 도착해도 컴포저가 섣불리 활성화되지 않도록 content 유무 + sentinel 로 가드한다.
  // (질문 status 는 CREATED 로 들어오므로 status 기준으로 거르면 안 된다.)
  const content = (last?.content ?? '').trim()
  const askable =
    last?.role === 'INTERVIEWER' &&
    content.length > 0 &&
    content !== FOLLOWUP_GENERATING_TEXT
  return askable ? 'AWAITING_ANSWER' : 'WAITING_FOR_QUESTION'
}

export function canSubmitAnswer(session: Session, messages: Message[]): boolean {
  return session.status === 'IN_PROGRESS' && currentTurn(messages) === 'AWAITING_ANSWER'
}

export function sessionProgress(session: Session): { current: number; max: number; ratio: number } {
  const max = session.maxQuestions ?? 0
  const current = session.totalQuestionCount ?? 0
  const ratio = max > 0 ? Math.min(current / max, 1) : 0
  return { current, max, ratio }
}
