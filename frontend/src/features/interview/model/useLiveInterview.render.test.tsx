import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ReactNode } from 'react'
import { renderHook, act, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { Message } from '@/domain/session'
import { useLiveInterview } from './useLiveInterview'
import { FOLLOWUP_GENERATING_TEXT } from './streamingBuffer'
import { sessionKeys } from './useSession'
import { messageKeys } from './useSessionMessages'

vi.mock('@/features/interview/api/streamToken', () => ({
  fetchSessionStreamToken: vi.fn().mockResolvedValue('tok'),
}))

// jsdom 에 없는 WebSocket — 테스트가 프레임을 직접 밀어 넣는다.
class FakeWS {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSING = 2
  static CLOSED = 3
  static last: FakeWS | null = null
  readyState = FakeWS.CONNECTING
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  url: string
  constructor(url: string) {
    this.url = url
    FakeWS.last = this
  }
  send() {}
  close() {
    this.readyState = FakeWS.CLOSED
  }
}

class FakeAudio {
  src = ''
  addEventListener() {}
  removeEventListener() {}
  play() {
    return Promise.resolve()
  }
  pause() {}
}

vi.stubGlobal('WebSocket', FakeWS)
vi.stubGlobal('Audio', FakeAudio)

const SESSION_ID = 7
const PLACEHOLDER_ID = 503

function buildMessages(): Message[] {
  const out: Message[] = []
  for (let i = 0; i < 10; i++) {
    out.push({
      id: 100 + i * 2,
      sessionId: SESSION_ID,
      role: 'INTERVIEWER',
      content: `질문 ${i}`,
      sequenceNumber: i * 2 + 1,
    } as Message)
    out.push({
      id: 101 + i * 2,
      sessionId: SESSION_ID,
      role: 'INTERVIEWEE',
      content: `답변 ${i}`,
      sequenceNumber: i * 2 + 2,
      parentMessageId: 100 + i * 2,
    } as Message)
  }
  out.push({
    id: PLACEHOLDER_ID,
    sessionId: SESSION_ID,
    role: 'INTERVIEWER',
    content: FOLLOWUP_GENERATING_TEXT,
    sequenceNumber: 21,
  } as Message)
  return out
}

function setup() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity, refetchOnWindowFocus: false } },
  })
  qc.setQueryData(sessionKeys.detail(SESSION_ID), {
    id: SESSION_ID,
    status: 'IN_PROGRESS',
    maxQuestions: 15,
    generalQuestionCount: 10,
    totalQuestionCount: 11,
  })
  qc.setQueryData(messageKeys.list(SESSION_ID), buildMessages())
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return renderHook(() => useLiveInterview(SESSION_ID), { wrapper })
}

function pushDelta(seq: number, text: string) {
  act(() => {
    FakeWS.last?.onmessage?.({
      data: JSON.stringify({
        id: String(seq),
        event: 'SESSION_MESSAGE_DELTA',
        data: { data: { messageId: PLACEHOLDER_ID, seq, text } },
      }),
    })
  })
}

beforeEach(() => {
  FakeWS.last = null
})

// 델타 폭풍 시나리오의 재계산·참조 안정성 계측 (A3 회귀 가드).
// baseline(개선 전) 수치는 evidence 2026-08-22-A3-렌더성능-baseline/ 에 기록되어 있다.
describe('useLiveInterview 렌더 안정성 (A3 계측)', () => {
  it('델타 1건당 참조가 유지되는 item 수', async () => {
    const { result } = setup()
    await waitFor(() => expect(FakeWS.last).not.toBeNull())
    expect(result.current.items).toHaveLength(21)

    const before = result.current.items
    pushDelta(0, '동시성 ')
    const after = result.current.items

    const kept = after.filter((it, i) => it === before[i]).length
    // 스트리밍 중인 placeholder 1개만 새 객체 — 나머지 20개는 참조 유지 (baseline: 0개).
    expect(kept).toBe(20)
    expect(after[20].content).toBe('동시성 ')
    expect(after[20].streaming).toBe(true)
  })

  it('델타와 무관한 리렌더에서 items 배열 참조', async () => {
    const { result, rerender } = setup()
    await waitFor(() => expect(FakeWS.last).not.toBeNull())

    const before = result.current.items
    rerender()
    // 입력이 안 바뀐 리렌더에서는 배열 자체가 그대로다 (baseline: 전부 새 참조).
    expect(result.current.items).toBe(before)
  })

  it('콜백 참조 안정성 (memo 자식의 전제)', async () => {
    const { result } = setup()
    await waitFor(() => expect(FakeWS.last).not.toBeNull())

    const first = {
      submitVoice: result.current.submitVoice,
      endSession: result.current.endSession,
      interruptSession: result.current.interruptSession,
      refetchSession: result.current.refetchSession,
    }
    pushDelta(0, '안녕')
    // memo 자식(AnswerComposer 등)이 bail out 하려면 콜백 참조가 유지되어야 한다 (baseline: 전부 새 함수).
    expect(result.current.submitVoice).toBe(first.submitVoice)
    expect(result.current.endSession).toBe(first.endSession)
    expect(result.current.interruptSession).toBe(first.interruptSession)
    expect(result.current.refetchSession).toBe(first.refetchSession)
  })
})
