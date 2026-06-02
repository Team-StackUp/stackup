import { describe, it, expect } from 'vitest'
import type { Message } from '@/domain/session'
import { pendingAnswers, toOptimisticMessage } from './optimistic'

const answerMsg = (content: string): Message => ({ role: 'INTERVIEWEE', content } as Message)

describe('pendingAnswers', () => {
  it('서버에 같은 내용 답변이 없으면 유지', () => {
    const result = pendingAnswers([{ tempId: 't1', content: '내 답변' }], [])
    expect(result).toHaveLength(1)
  })
  it('서버에 같은 내용 답변이 있으면 제거', () => {
    const result = pendingAnswers([{ tempId: 't1', content: '내 답변' }], [answerMsg('내 답변')])
    expect(result).toHaveLength(0)
  })
  it('동일 내용 낙관적 2개 vs 서버 1개 → 1:1 소진으로 1개만 남는다', () => {
    const result = pendingAnswers(
      [
        { tempId: 't1', content: '네' },
        { tempId: 't2', content: '네' },
      ],
      [answerMsg('네')],
    )
    expect(result).toHaveLength(1)
    expect(result[0].tempId).toBe('t2')
  })
})

describe('toOptimisticMessage', () => {
  it('INTERVIEWEE 메시지로 변환', () => {
    const m = toOptimisticMessage({ tempId: 't1', content: '안녕' })
    expect(m.role).toBe('INTERVIEWEE')
    expect(m.content).toBe('안녕')
  })
})
