import { describe, it, expect } from 'vitest'
import { interviewEventAction } from './interviewEvent'

describe('interviewEventAction', () => {
  it('session.message → 메시지 refetch', () => {
    expect(interviewEventAction('session.message')).toEqual({ kind: 'refetch-messages' })
  })
  it('session.state → 세션 refetch', () => {
    expect(interviewEventAction('session.state')).toEqual({ kind: 'refetch-session' })
  })
  it('feedback.ready → 피드백 리다이렉트', () => {
    expect(interviewEventAction('feedback.ready')).toEqual({ kind: 'redirect-feedback' })
  })
  it('알 수 없는 이벤트는 무시', () => {
    expect(interviewEventAction('whatever')).toEqual({ kind: 'ignore' })
  })
})
