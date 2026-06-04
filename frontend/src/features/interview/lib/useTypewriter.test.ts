import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useTypewriter } from './useTypewriter'

describe('useTypewriter', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('enabled=false 면 전체 텍스트 즉시', () => {
    const { result } = renderHook(() => useTypewriter('안녕하세요', false))
    expect(result.current).toBe('안녕하세요')
  })

  it('enabled 면 점진적으로 드러나 결국 전체에 도달', () => {
    const { result } = renderHook(() => useTypewriter('가나다라마바사아자차', true))
    // 처음엔 일부만(또는 비어있음)
    const initial = result.current.length
    act(() => { vi.advanceTimersByTime(35 * 12) })
    expect(result.current).toBe('가나다라마바사아자차')
    expect(initial).toBeLessThan('가나다라마바사아자차'.length)
  })
})
