import { describe, it, expect } from 'vitest'
import { applyDelta, isStreamingMessage, FOLLOWUP_GENERATING_TEXT } from './streamingBuffer'

describe('streamingBuffer', () => {
  it('applyDelta 는 messageId별로 누적한다', () => {
    let buf: Record<number, string> = {}
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: '안녕' })
    buf = applyDelta(buf, { messageId: 5, seq: 1, text: '하세요' })
    expect(buf[5]).toBe('안녕하세요')
  })
  it('isStreamingMessage 는 sentinel content + 버퍼 유무로 판별', () => {
    expect(isStreamingMessage({ content: FOLLOWUP_GENERATING_TEXT }, undefined)).toBe(true)
    expect(isStreamingMessage({ content: FOLLOWUP_GENERATING_TEXT }, '안녕')).toBe(true)
    expect(isStreamingMessage({ content: '완성된 질문' }, undefined)).toBe(false)
  })
})
