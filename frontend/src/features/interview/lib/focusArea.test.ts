import { describe, it, expect } from 'vitest'
import { focusAreaLabel } from './focusArea'

describe('focusAreaLabel', () => {
  it('알려진 축은 한국어 표시명으로 바꾼다', () => {
    expect(focusAreaLabel('TECHNICAL')).toBe('기술 정확도')
    expect(focusAreaLabel('LOGIC')).toBe('논리력')
    expect(focusAreaLabel('COMMUNICATION')).toBe('전달력')
  })

  // 서버가 축을 늘려도 화면이 빈칸이 되지 않아야 한다.
  it('모르는 축은 원본을 그대로 쓴다', () => {
    expect(focusAreaLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })
})
