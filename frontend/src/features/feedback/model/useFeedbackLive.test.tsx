import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ReactNode } from 'react'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from '@/shared/api'
import { useFeedbackLive } from './useFeedbackLive'
import { getFeedback } from '../api/feedbackApi'

vi.mock('../api/feedbackApi', () => ({
  getFeedback: vi.fn(),
}))

// SSE 를 흉내 내는 EventSource — 테스트가 FEEDBACK_READY 를 직접 발화한다.
class FakeES {
  static last: FakeES | null = null
  handlers: Record<string, (e: { data: string }) => void> = {}
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  closed = false
  url: string
  constructor(url: string) {
    this.url = url
    FakeES.last = this
  }
  addEventListener(name: string, fn: (e: { data: string }) => void) {
    this.handlers[name] = fn
  }
  close() {
    this.closed = true
  }
  emit(name: string, payload: unknown) {
    this.handlers[name]?.({ data: JSON.stringify(payload) })
  }
}

vi.stubGlobal('EventSource', FakeES)

const notReady = () => new ApiError(404, { code: 'FEEDBACK_NOT_READY', message: '생성 중' })

function setup() {
  const qc = new QueryClient({
    defaultOptions: { queries: { refetchOnWindowFocus: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  return renderHook(() => useFeedbackLive(99, async () => 'tok'), { wrapper })
}

beforeEach(() => {
  FakeES.last = null
  vi.mocked(getFeedback).mockReset()
})

describe('useFeedbackLive', () => {
  it('FEEDBACK_READY 수신 즉시 재조회한다 — 3s 폴링 간격을 기다리지 않는다', async () => {
    // 첫 조회는 생성 중(404) → 재시도 대기(3s) 상태로 들어간다.
    vi.mocked(getFeedback)
      .mockRejectedValueOnce(notReady())
      .mockResolvedValue({ sessionId: 99, overallScore: 80 } as never)

    const { result } = setup()
    await waitFor(() => expect(FakeES.last).not.toBeNull())
    await waitFor(() => expect(getFeedback).toHaveBeenCalledTimes(1))
    expect(result.current.data).toBeUndefined()

    act(() => FakeES.last?.emit('FEEDBACK_READY', { data: { sessionId: 99 } }))

    // 3s 재시도 타이머보다 훨씬 이른 시점에 데이터가 도착해야 한다.
    await waitFor(() => expect(result.current.data).toBeDefined(), { timeout: 1_500 })
    expect(result.current.data?.overallScore).toBe(80)
  })

  it('피드백이 도착하면 스트림을 닫는다', async () => {
    vi.mocked(getFeedback).mockResolvedValue({ sessionId: 99, overallScore: 80 } as never)

    const { result } = setup()
    await waitFor(() => expect(result.current.data).toBeDefined())
    // path 가 null 이 되어 연결 effect 가 정리된다.
    await waitFor(() => expect(FakeES.last?.closed ?? true).toBe(true))
  })

  it('데이터가 이미 표시 중일 때 늦게 온 이벤트는 무시한다 — 스켈레톤으로 되돌아가지 않는다', async () => {
    vi.mocked(getFeedback).mockResolvedValue({ sessionId: 99, overallScore: 80 } as never)

    const { result } = setup()
    await waitFor(() => expect(result.current.data).toBeDefined())
    const callsAfterLoad = vi.mocked(getFeedback).mock.calls.length

    // 스트림 close 전 좁은 레이스 창에서 지연 도착한 이벤트를 재현.
    act(() => FakeES.last?.emit('FEEDBACK_READY', { data: { sessionId: 99 } }))

    expect(result.current.data?.overallScore).toBe(80)
    expect(vi.mocked(getFeedback).mock.calls.length).toBe(callsAfterLoad)
  })
  it('FEEDBACK_PROGRESS 수신 시 진행 문구·카운터를 노출한다 (B2)', async () => {
    vi.mocked(getFeedback).mockRejectedValue(notReady())

    const { result } = setup()
    await waitFor(() => expect(FakeES.last).not.toBeNull())
    expect(result.current.progress).toBeNull()

    act(() =>
      FakeES.last?.emit('FEEDBACK_PROGRESS', {
        data: { sessionId: 99, phase: 'SCORING', message: '세부 평가를 진행하고 있어요. (2/5)', completed: 2, total: 5 },
      }),
    )
    expect(result.current.progress).toEqual({
      message: '세부 평가를 진행하고 있어요. (2/5)',
      completed: 2,
      total: 5,
    })

    // 카운터 없는 진행 이벤트(PREPARING/FINALIZING)는 message 만 채워진다.
    act(() =>
      FakeES.last?.emit('FEEDBACK_PROGRESS', {
        data: { sessionId: 99, phase: 'FINALIZING', message: '피드백 리포트를 정리하고 있어요.' },
      }),
    )
    expect(result.current.progress).toEqual({
      message: '피드백 리포트를 정리하고 있어요.',
      completed: null,
      total: null,
    })
  })

  it('message 없는 비정상 FEEDBACK_PROGRESS 페이로드는 무시한다', async () => {
    vi.mocked(getFeedback).mockRejectedValue(notReady())

    const { result } = setup()
    await waitFor(() => expect(FakeES.last).not.toBeNull())

    act(() => FakeES.last?.emit('FEEDBACK_PROGRESS', { data: { completed: 1 } }))
    act(() => FakeES.last?.emit('FEEDBACK_PROGRESS', {}))
    expect(result.current.progress).toBeNull()
  })
})
