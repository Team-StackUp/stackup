import { describe, it, expect } from 'vitest'
import { interviewEventAction } from './interviewEvent'

describe('interviewEventAction', () => {
  it('SESSION_MESSAGE → 메시지 refetch', () => {
    expect(interviewEventAction('SESSION_MESSAGE')).toEqual({ kind: 'refetch-messages' })
  })
  it('SESSION_STATE → 세션 refetch', () => {
    expect(interviewEventAction('SESSION_STATE')).toEqual({ kind: 'refetch-session' })
  })
  it('FEEDBACK_READY → 피드백 리다이렉트', () => {
    expect(interviewEventAction('FEEDBACK_READY')).toEqual({ kind: 'redirect-feedback' })
  })
  it('소문자 점표기(문서상 예시)는 매칭되지 않는다', () => {
    expect(interviewEventAction('session.message')).toEqual({ kind: 'ignore' })
  })
  it('알 수 없는 이벤트는 무시', () => {
    expect(interviewEventAction('whatever')).toEqual({ kind: 'ignore' })
  })
  it('SESSION_MESSAGE_DELTA → 델타 append', () => {
    expect(interviewEventAction('SESSION_MESSAGE_DELTA')).toEqual({ kind: 'append-delta' })
  })
  it('SESSION_MESSAGE_AUDIO → 오디오 큐', () => {
    expect(interviewEventAction('SESSION_MESSAGE_AUDIO')).toEqual({ kind: 'queue-audio' })
  })
})
