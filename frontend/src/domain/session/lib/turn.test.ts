import { describe, it, expect } from 'vitest'
import type { Message, Session } from '../model/types'
import { currentTurn, canSubmitAnswer, sessionProgress, isQuestion, isAnswer } from './turn'

const msg = (role: 'INTERVIEWER' | 'INTERVIEWEE', content = '내용'): Message =>
  ({ role, content } as Message)
const session = (over: Partial<Session>): Session => over as Session

describe('currentTurn', () => {
  it('마지막이 내용 있는 면접관 질문이면 답변 차례', () => {
    expect(currentTurn([msg('INTERVIEWER')])).toBe('AWAITING_ANSWER')
  })
  it('마지막이 답변이면 질문 대기', () => {
    expect(currentTurn([msg('INTERVIEWER'), msg('INTERVIEWEE')])).toBe('WAITING_FOR_QUESTION')
  })
  it('비어 있으면 질문 대기', () => {
    expect(currentTurn([])).toBe('WAITING_FOR_QUESTION')
  })
  it('내용 없는 질문(생성 중)이면 질문 대기', () => {
    expect(currentTurn([msg('INTERVIEWER', '')])).toBe('WAITING_FOR_QUESTION')
  })
})

describe('canSubmitAnswer', () => {
  it('IN_PROGRESS이고 답변 차례면 true', () => {
    expect(canSubmitAnswer(session({ status: 'IN_PROGRESS' }), [msg('INTERVIEWER')])).toBe(true)
  })
  it('세션이 진행중이 아니면 false', () => {
    expect(canSubmitAnswer(session({ status: 'READY' }), [msg('INTERVIEWER')])).toBe(false)
  })
})

describe('sessionProgress', () => {
  it('현재/최대/비율을 계산', () => {
    expect(sessionProgress(session({ totalQuestionCount: 2, maxQuestions: 5 }))).toEqual({
      current: 2, max: 5, ratio: 0.4,
    })
  })
  it('max가 0이면 ratio 0', () => {
    expect(sessionProgress(session({ totalQuestionCount: 0, maxQuestions: 0 })).ratio).toBe(0)
  })
})

describe('isQuestion/isAnswer', () => {
  it('역할을 판별', () => {
    expect(isQuestion(msg('INTERVIEWER'))).toBe(true)
    expect(isAnswer(msg('INTERVIEWEE'))).toBe(true)
  })
})
