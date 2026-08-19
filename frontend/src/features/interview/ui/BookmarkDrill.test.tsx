import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BookmarkDrill } from './BookmarkDrill'
import type { BookmarkedQuestion } from '../api/bookmarkApi'

const items: BookmarkedQuestion[] = [
  {
    messageId: 100,
    sessionId: 7,
    sessionTitle: '백엔드 모의면접',
    category: 'CS_FUNDAMENTAL',
    question: 'ACID 를 설명해 주세요.',
    expectedSignal: '4대 속성을 예시와 함께',
    myAnswer: '원자성만 말했습니다',
    modelAnswer: '원자성·일관성·격리성·지속성',
    coachingComment: '나머지도 짚어보세요',
    createdAt: '2026-08-18T00:00:00Z',
  },
  {
    messageId: 101,
    sessionId: 7,
    sessionTitle: '백엔드 모의면접',
    category: 'TECH_CHOICE',
    question: '왜 Kafka 를 골랐나요?',
    createdAt: '2026-08-18T00:00:00Z',
  },
]

beforeEach(() => window.localStorage.clear())

describe('BookmarkDrill', () => {
  // 드릴의 핵심 — 먼저 답해 보고 그 다음에 정답을 본다.
  it('정답 확인 전에는 모범 답안이 보이지 않는다', async () => {
    render(<BookmarkDrill items={items} onExit={vi.fn()} />)

    expect(screen.getByText('ACID 를 설명해 주세요.')).toBeInTheDocument()
    expect(screen.queryByText('원자성·일관성·격리성·지속성')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '정답 확인' }))

    expect(screen.getByText('원자성·일관성·격리성·지속성')).toBeInTheDocument()
    expect(screen.getByText('원자성만 말했습니다')).toBeInTheDocument()
  })

  it('다음 질문으로 넘어가면 정답이 다시 가려진다', async () => {
    render(<BookmarkDrill items={items} onExit={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '정답 확인' }))
    await userEvent.click(screen.getByRole('button', { name: '다음 질문' }))

    expect(screen.getByText('왜 Kafka 를 골랐나요?')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '정답 확인' })).toBeInTheDocument()
  })

  // 기록이 없는 질문도 드릴에 포함된다 — 펼쳤을 때 이유를 알려준다.
  it('복습 재료가 없는 질문은 이유를 보여준다', async () => {
    render(<BookmarkDrill items={[items[1]]} onExit={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '정답 확인' }))

    expect(
      screen.getByText('이 질문에는 아직 답변·피드백 기록이 없어요.'),
    ).toBeInTheDocument()
  })

  it('마지막 질문을 마치면 완료 화면을 보여준다', async () => {
    render(<BookmarkDrill items={[items[0]]} onExit={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '정답 확인' }))
    await userEvent.click(screen.getByRole('button', { name: '복습 마치기' }))

    expect(screen.getByText('복습을 마쳤습니다')).toBeInTheDocument()
  })

  it('목록으로 나갈 수 있다', async () => {
    const onExit = vi.fn()
    render(<BookmarkDrill items={items} onExit={onExit} />)

    await userEvent.click(screen.getByRole('button', { name: '목록으로' }))

    expect(onExit).toHaveBeenCalled()
  })

  // 적어둔 답은 다시 들어와도 남아 있어야 한다.
  it('적은 답변이 재진입 후에도 남는다', async () => {
    const { unmount } = render(<BookmarkDrill items={items} onExit={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/다시 답해 보기/), '지금이라면 이렇게')
    unmount()

    render(<BookmarkDrill items={items} onExit={vi.fn()} />)

    expect(screen.getByLabelText(/다시 답해 보기/)).toHaveValue('지금이라면 이렇게')
  })
})
