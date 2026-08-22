import { describe, it, expect } from 'vitest'
import type { DeltaBuffer } from './streamingBuffer'
import {
  applyDelta,
  bufferedText,
  isStreamingMessage,
  FOLLOWUP_GENERATING_TEXT,
} from './streamingBuffer'

describe('streamingBuffer', () => {
  it('정상 순서 프레임을 누적한다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: '안녕' })
    buf = applyDelta(buf, { messageId: 5, seq: 1, text: '하세요' })
    expect(bufferedText(buf, 5)).toBe('안녕하세요')
  })

  it('순서가 뒤바뀐 프레임은 seq 로 재조립한다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 7, seq: 0, text: '동시성 ' })
    buf = applyDelta(buf, { messageId: 7, seq: 2, text: '어떻게 해결했나요?' })
    buf = applyDelta(buf, { messageId: 7, seq: 1, text: '문제를 ' })
    expect(bufferedText(buf, 7)).toBe('동시성 문제를 어떻게 해결했나요?')
  })

  it('중복 재전달 프레임은 멱등 — 한 번만 반영된다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: '안녕' })
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: '안녕' })
    expect(bufferedText(buf, 5)).toBe('안녕')
  })

  it('갭 뒤 프레임은 갭이 채워질 때까지 표시하지 않는다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: 'A' })
    buf = applyDelta(buf, { messageId: 5, seq: 2, text: 'C' })
    expect(bufferedText(buf, 5)).toBe('A')
  })

  it('갭이 채워지면 보류된 프레임까지 이어서 표시한다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 5, seq: 0, text: 'A' })
    buf = applyDelta(buf, { messageId: 5, seq: 2, text: 'C' })
    buf = applyDelta(buf, { messageId: 5, seq: 1, text: 'B' })
    expect(bufferedText(buf, 5)).toBe('ABC')
  })

  it('seq 0 이 도착하기 전에는 undefined — placeholder 표시를 유지한다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 5, seq: 1, text: 'B' })
    expect(bufferedText(buf, 5)).toBeUndefined()
  })

  it('메시지별로 격리 누적한다', () => {
    let buf: DeltaBuffer = {}
    buf = applyDelta(buf, { messageId: 1, seq: 0, text: '하나' })
    buf = applyDelta(buf, { messageId: 2, seq: 0, text: '둘' })
    expect(bufferedText(buf, 1)).toBe('하나')
    expect(bufferedText(buf, 2)).toBe('둘')
    expect(bufferedText(buf, 3)).toBeUndefined()
  })

  it('isStreamingMessage 는 sentinel content + 버퍼 유무로 판별', () => {
    expect(isStreamingMessage({ content: FOLLOWUP_GENERATING_TEXT }, undefined)).toBe(true)
    expect(isStreamingMessage({ content: FOLLOWUP_GENERATING_TEXT }, '안녕')).toBe(true)
    expect(isStreamingMessage({ content: '완성된 질문' }, undefined)).toBe(false)
  })
})
