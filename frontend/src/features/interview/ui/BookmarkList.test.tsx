import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { BookmarkList } from './BookmarkList'
import type { BookmarkedQuestion } from '../api/bookmarkApi'

const useBookmarks = vi.fn()
const removeMutate = vi.fn()
vi.mock('../model/useBookmarks', () => ({
  useBookmarks: () => useBookmarks(),
  useSetQuestionBookmark: () => ({ mutate: removeMutate, isPending: false }),
}))

const item: BookmarkedQuestion = {
  messageId: 100,
  sessionId: 7,
  sessionTitle: '백엔드 모의면접',
  category: 'CS_FUNDAMENTAL',
  question: 'ACID 를 설명해 주세요.',
  expectedSignal: '트랜잭션 4대 속성을 예시와 함께',
  myAnswer: '원자성만 말했습니다',
  modelAnswer: '원자성·일관성·격리성·지속성을…',
  coachingComment: '나머지 3가지도 짚어보세요',
  createdAt: '2026-08-18T00:00:00Z',
}

function renderList(data: BookmarkedQuestion[]) {
  useBookmarks.mockReturnValue({
    data,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  })
  return render(
    <MemoryRouter>
      <BookmarkList />
    </MemoryRouter>,
  )
}

beforeEach(() => removeMutate.mockClear())

describe('BookmarkList', () => {
  it('담아둔 게 없으면 담는 방법을 안내한다', () => {
    renderList([])

    expect(screen.getByText('아직 담아둔 질문이 없어요')).toBeInTheDocument()
  })

  // 복습의 핵심은 먼저 스스로 떠올려 보는 것 — 모범 답안이 처음부터 보이면 의미가 없다.
  it('모범 답안은 접혀 있고 펼쳐야 보인다', async () => {
    renderList([item])

    expect(screen.getByText('ACID 를 설명해 주세요.')).toBeInTheDocument()
    expect(screen.getByText(/평가 관점/)).toBeInTheDocument()
    expect(screen.queryByText('원자성·일관성·격리성·지속성을…')).not.toBeInTheDocument()

    await userEvent.click(
      screen.getByRole('button', { name: '내 답변 · 모범 답안 보기' }),
    )

    expect(screen.getByText('원자성·일관성·격리성·지속성을…')).toBeInTheDocument()
    expect(screen.getByText('원자성만 말했습니다')).toBeInTheDocument()
    expect(screen.getByText('나머지 3가지도 짚어보세요')).toBeInTheDocument()
  })

  // 답변 전에 담았거나 피드백이 아직 없는 경우 — 빈 아코디언 대신 이유를 보여준다.
  it('복습 재료가 없으면 이유를 알려준다', () => {
    // 서버는 값이 없으면 필드를 비운다(생성 타입상 optional).
    renderList([
      { ...item, myAnswer: undefined, modelAnswer: undefined, coachingComment: undefined },
    ])

    expect(
      screen.getByText('이 질문에는 아직 답변·피드백 기록이 없어요.'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: '내 답변 · 모범 답안 보기' }),
    ).not.toBeInTheDocument()
  })

  it('출처 면접으로 돌아갈 수 있다', () => {
    renderList([item])

    expect(screen.getByRole('link', { name: '백엔드 모의면접' })).toHaveAttribute(
      'href',
      '/sessions/7',
    )
  })

  // 해제는 토글이 아니라 bookmarked=false 를 명시해 보낸다(재전송이 상태를 뒤집지 않게).
  it('빼기는 명시적으로 false 를 보낸다', async () => {
    renderList([item])

    await userEvent.click(screen.getByRole('button', { name: '오답노트에서 빼기' }))

    expect(removeMutate).toHaveBeenCalledWith({ messageId: 100, bookmarked: false })
  })
})
