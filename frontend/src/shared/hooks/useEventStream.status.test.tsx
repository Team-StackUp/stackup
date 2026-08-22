import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useEventStream } from './useEventStream'

// jsdom 에 없는 EventSource — 테스트가 open/error 를 직접 발화한다.
class FakeES {
  static last: FakeES | null = null
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  url: string
  constructor(url: string) {
    this.url = url
    FakeES.last = this
  }
  addEventListener() {}
  close() {}
}

vi.stubGlobal('EventSource', FakeES)

function setup(getToken: () => Promise<string | null> = async () => 'tok') {
  return renderHook(() =>
    useEventStream({
      path: '/realtime/stream/me',
      getToken,
      handlers: { DOC_STATE: () => {} },
    }),
  )
}

beforeEach(() => {
  FakeES.last = null
})

describe('useEventStream 연결 상태', () => {
  it('최초엔 connecting, 열리면 open', async () => {
    const { result } = setup()
    expect(result.current).toBe('connecting')
    await waitFor(() => expect(FakeES.last).not.toBeNull())
    act(() => FakeES.last?.onopen?.())
    expect(result.current).toBe('open')
  })

  it('끊기면 closed 로 승격되고, 재연결이 성사되기 전까지 유지된다', async () => {
    vi.useFakeTimers()
    try {
      const { result } = setup()
      await act(async () => {
        await vi.runOnlyPendingTimersAsync()
      })
      act(() => FakeES.last?.onopen?.())
      expect(result.current).toBe('open')

      act(() => FakeES.last?.onerror?.())
      expect(result.current).toBe('closed')

      // 백오프 재시도 대기 중에도 connecting 으로 되돌아가지 않는다 (배너 깜빡임 방지).
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1_100)
      })
      expect(result.current).toBe('closed')

      // 재연결 성사 시에만 open 복귀.
      act(() => FakeES.last?.onopen?.())
      expect(result.current).toBe('open')
    } finally {
      vi.useRealTimers()
    }
  })

  it('언마운트 후 늦게 실패한 토큰 발급은 재연결을 스케줄하지 않는다', async () => {
    vi.useFakeTimers()
    try {
      let reject: ((e: Error) => void) | undefined
      const { unmount } = setup(
        () => new Promise<string | null>((_res, rej) => (reject = rej)),
      )
      unmount()

      await act(async () => {
        reject?.(new Error('late failure'))
        await vi.advanceTimersByTimeAsync(60_000)
      })
      // 버려진 effect 가 재연결 타이머를 만들었다면 새 EventSource 가 생겼을 것.
      expect(FakeES.last).toBeNull()
    } finally {
      vi.useRealTimers()
    }
  })
})
