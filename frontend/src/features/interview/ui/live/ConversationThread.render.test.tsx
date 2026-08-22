import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import type { ThreadItem } from '../../model/useLiveInterview'
import { ConversationThread } from './ConversationThread'

// 버블 본문이 실행될 때만 호출되는 내부 훅을 카운터로 바꿔 렌더 수를 센다
// (React.memo 가 bail out 하면 본문이 실행되지 않아 카운트되지 않는다).
const counts = { question: 0, answer: 0 }

vi.mock('../../lib/media/useTtsPlayback', () => ({
  useTtsPlayback: () => {
    counts.question += 1
    return { playing: false, toggle: () => {}, audioNode: null }
  },
}))
vi.mock('../../lib/media/useMessageAudio', () => ({
  useMessageAudio: () => {
    counts.answer += 1
    return { url: undefined, load: async () => undefined }
  },
}))
vi.mock('../../model/useBookmarks', () => ({
  useSetQuestionBookmark: () => ({ mutate: () => {}, isPending: false }),
}))

const items: ThreadItem[] = []
for (let i = 0; i < 10; i++) {
  items.push({
    id: 100 + i * 2,
    key: `m-${100 + i * 2}`,
    role: 'INTERVIEWER',
    content: `질문 ${i}`,
    sequenceNumber: i * 2 + 1,
  } as ThreadItem)
  items.push({
    id: 101 + i * 2,
    key: `m-${101 + i * 2}`,
    role: 'INTERVIEWEE',
    content: `답변 ${i}`,
    sequenceNumber: i * 2 + 2,
  } as ThreadItem)
}

function Harness({ list }: { list: ThreadItem[] }) {
  return <ConversationThread items={list} awaitingQuestion={false} />
}

beforeEach(() => {
  counts.question = 0
  counts.answer = 0
})

// baseline(개선 전) 수치는 evidence 2026-08-22-A3-렌더성능-baseline/ 에 기록되어 있다.
describe('ConversationThread 버블 렌더 수 (A3 계측)', () => {
  it('items 참조가 그대로인 부모 리렌더에서 버블 본문은 재실행되지 않는다', () => {
    const { rerender } = render(<Harness list={items} />)
    expect(counts.question).toBe(10)
    expect(counts.answer).toBe(10)

    rerender(<Harness list={items} />)
    // React.memo bail out (baseline: 20/20 전부 재실행).
    expect(counts.question).toBe(10)
    expect(counts.answer).toBe(10)
  })

  it('델타로 item 1개만 바뀌면 해당 버블만 재실행된다 (드로어 열림 + 스트리밍 시나리오)', () => {
    const { rerender } = render(<Harness list={items} />)
    expect(counts.question).toBe(10)
    expect(counts.answer).toBe(10)

    // useLiveInterview 가 델타 1건에 하는 일과 동일: 배열은 새로, 스트리밍 중인 1개만 새 객체.
    const next = items.map((it, i) => (i === 18 ? { ...it, content: '질문 9 + 델타' } : it))
    rerender(<Harness list={next} />)
    expect(counts.question).toBe(11)
    expect(counts.answer).toBe(10)
  })
})
