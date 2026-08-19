import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QuestionBubble } from './QuestionBubble'
import type { Message } from '@/domain/session'

const bookmarkMutate = vi.fn()
vi.mock('../../model/useBookmarks', () => ({
  useSetQuestionBookmark: () => ({ mutate: bookmarkMutate, isPending: false }),
}))
vi.mock('../../lib/media/useTtsPlayback', () => ({
  useTtsPlayback: () => ({ playing: false, toggle: vi.fn(), audioNode: null }),
}))

const question: Message = {
  id: 100,
  sessionId: 7,
  role: 'INTERVIEWER',
  content: 'ACID 를 설명해 주세요.',
  category: 'CS_FUNDAMENTAL',
  sequenceNumber: 1,
}

beforeEach(() => bookmarkMutate.mockClear())

describe('QuestionBubble', () => {
  // 라이브 중에는 별을 숨긴다 — 답변에 집중해야 할 때 끼어드는 조작이다.
  it('기본적으로는 오답노트 버튼을 노출하지 않는다', () => {
    render(<QuestionBubble message={question} />)

    expect(screen.queryByRole('button', { name: /오답노트/ })).not.toBeInTheDocument()
  })

  it('담기를 누르면 bookmarked=true 를 보낸다', async () => {
    render(<QuestionBubble message={question} bookmarkable />)

    const button = screen.getByRole('button', { name: '오답노트에 담기' })
    expect(button).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(button)

    expect(bookmarkMutate).toHaveBeenCalledWith({ messageId: 100, bookmarked: true })
  })

  it('이미 담긴 질문은 빼기로 동작한다', async () => {
    render(<QuestionBubble message={{ ...question, bookmarked: true }} bookmarkable />)

    const button = screen.getByRole('button', { name: '오답노트에서 빼기' })
    expect(button).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(button)

    expect(bookmarkMutate).toHaveBeenCalledWith({ messageId: 100, bookmarked: false })
  })
})
