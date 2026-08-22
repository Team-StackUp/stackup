import { describe, it, expect, vi, afterEach } from 'vitest'
import { analysisProgress } from './useAnalysisProgress'

afterEach(() => {
  analysisProgress.clear('RESUME', 1)
  analysisProgress.clear('RESUME', 2)
  vi.useRealTimers()
})

describe('analysisProgress TTL', () => {
  it('갱신이 끊긴 항목은 TTL(90s) 후 걷어낸다 — 종료 이벤트 유실 시 영구 고착 방지', () => {
    vi.useFakeTimers()
    analysisProgress.set('RESUME', 1, { phase: 'EMBEDDING', message: '임베딩 중…' })
    expect(analysisProgress.get('RESUME', 1)?.message).toBe('임베딩 중…')

    vi.advanceTimersByTime(91_000 + 15_000)
    expect(analysisProgress.get('RESUME', 1)).toBeUndefined()
  })

  it('계속 갱신되는 항목은 살아있고, 끊긴 항목만 만료된다', () => {
    vi.useFakeTimers()
    analysisProgress.set('RESUME', 1, { phase: 'EXTRACTING', message: 'A' })
    analysisProgress.set('RESUME', 2, { phase: 'EXTRACTING', message: 'B' })

    // 1번만 60초마다 갱신 — 2번은 방치.
    for (let i = 0; i < 3; i++) {
      vi.advanceTimersByTime(60_000)
      analysisProgress.set('RESUME', 1, { phase: 'SUMMARIZING', message: `A${i}` })
    }
    expect(analysisProgress.get('RESUME', 1)).toBeDefined()
    expect(analysisProgress.get('RESUME', 2)).toBeUndefined()
  })

  it('구독자는 만료 시점에 알림을 받는다', () => {
    vi.useFakeTimers()
    const listener = vi.fn()
    const unsubscribe = analysisProgress.subscribe(listener)
    analysisProgress.set('RESUME', 1, { phase: 'EMBEDDING', message: '임베딩 중…' })
    listener.mockClear()

    vi.advanceTimersByTime(91_000 + 15_000)
    expect(listener).toHaveBeenCalled()
    unsubscribe()
  })
})
