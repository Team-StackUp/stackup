import type { Message, Session } from '../model/types'

export type Turn = 'AWAITING_ANSWER' | 'WAITING_FOR_QUESTION'

export function isQuestion(message: Message): boolean {
  return message.role === 'INTERVIEWER'
}

export function isAnswer(message: Message): boolean {
  return message.role === 'INTERVIEWEE'
}

export function currentTurn(messages: Message[]): Turn {
  const last = messages[messages.length - 1]
  // 마지막이 '내용이 있는' 면접관 질문일 때만 답변 차례. 질문 생성 중(빈 content)
  // 메시지가 먼저 도착해도 컴포저가 섣불리 활성화되지 않도록 content 유무로 가드한다.
  // (질문 status 는 CREATED 로 들어오므로 status 기준으로 거르면 안 된다.)
  const askable = last?.role === 'INTERVIEWER' && (last.content ?? '').trim().length > 0
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
