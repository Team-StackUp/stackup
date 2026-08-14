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

  // FAILED 는 두 sentinel 케이스를 가진다 — role 에 따라 판정이 정반대다.
  //  · INTERVIEWEE + FAILED: STT 실패. 백엔드가 "텍스트로 다시 답변해 주세요"로 content 를
  //    바꾸고 재답변을 받도록 설계돼 있다(resolveAnswerParent 의 FAILED+audio 분기).
  //    이걸 WAITING_FOR_QUESTION 으로 두면 컴포저가 영구 잠기고, 새로고침해도 재현된다.
  //  · INTERVIEWER + FAILED: 꼬리질문 생성 실패 placeholder("질문 생성에 실패했습니다…").
  //    content 가 있어서 아래 askable 조건을 통과해 버리면 실패 안내문에 답변이 달린다.
  //    백엔드가 곧 advanceToNextGeneral 로 다음 질문을 보내므로 대기가 맞다.
  if (last?.status === 'FAILED') {
    return last.role === 'INTERVIEWEE' ? 'AWAITING_ANSWER' : 'WAITING_FOR_QUESTION'
  }

  // 마지막이 '내용이 있는' 면접관 질문일 때만 답변 차례. 질문 생성 중(빈 content 또는 sentinel)
  // 메시지가 먼저 도착해도 컴포저가 섣불리 활성화되지 않도록 content 유무 + sentinel 로 가드한다.
  // (정상 질문의 status 는 CREATED 로 들어오므로 CREATED/COMPLETED 를 기준으로 거르면 안 된다 —
  //  위에서 FAILED 만 별도 처리하는 이유.)
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
  // 실제 면접은 일반질문 수(자기소개 포함 = generalQuestionCount)에서 끝나거나 maxQuestions 상한에서
  // 끝난다 — 둘 중 작은 값이 진짜 목표. maxQuestions(상한)로만 나누면 진행률이 과소 표시된다.
  const cap = session.maxQuestions ?? 0
  const general = session.generalQuestionCount ?? cap
  const target = cap > 0 ? Math.min(general || cap, cap) : general
  const current = session.totalQuestionCount ?? 0
  // 목표 개수는 예정된 일반질문 기준이라 꼬리질문·추가 질문이 붙으면 실제 질문 수가 이를
  // 넘어선다. 그대로 두면 헤더에 "질문 2 / 1" 처럼 분자가 분모보다 큰 표시가 나온다
  // (실제로 일반질문 1개 세션에서 발생). 이미 나온 질문 수까지는 분모를 밀어 올린다.
  const max = Math.max(target, current)
  const ratio = max > 0 ? Math.min(current / max, 1) : 0
  return { current, max, ratio }
}
