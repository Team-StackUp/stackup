import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SessionEndedPanel } from './SessionEndedPanel'

const retryMutate = vi.fn()
const lastSourceCount = vi.fn()
vi.mock('../../model/useRetrySession', () => ({
  useRetrySession: (count?: number) => {
    lastSourceCount(count)
    return { mutate: retryMutate, isPending: false }
  },
}))
vi.mock('../InterviewTranscript', () => ({
  InterviewTranscript: () => <div data-testid="transcript" />,
}))

beforeEach(() => {
  retryMutate.mockClear()
  lastSourceCount.mockClear()
})

function renderPanel(status: 'COMPLETED' | 'INTERRUPTED' | 'CANCELLED') {
  return render(
    <MemoryRouter>
      <SessionEndedPanel status={status} sessionId={7} />
    </MemoryRouter>,
  )
}

describe('SessionEndedPanel', () => {
  it('완료 세션은 피드백 링크와 기록을 함께 보여준다', () => {
    renderPanel('COMPLETED')

    expect(screen.getByRole('link', { name: '피드백 보기' })).toHaveAttribute(
      'href',
      '/sessions/7/feedback',
    )
    expect(screen.getByTestId('transcript')).toBeInTheDocument()
  })

  // 중단된 면접은 피드백이 없다 — 문답 기록이 유일한 결과물이다.
  it('중단 세션은 피드백 링크 없이 기록을 보여준다', () => {
    renderPanel('INTERRUPTED')

    expect(screen.queryByRole('link', { name: '피드백 보기' })).not.toBeInTheDocument()
    expect(screen.getByTestId('transcript')).toBeInTheDocument()
  })

  // 시작 전 취소라 보여줄 문답이 없다.
  it('취소 세션은 기록을 보여주지 않는다', () => {
    renderPanel('CANCELLED')

    expect(screen.queryByTestId('transcript')).not.toBeInTheDocument()
  })

  it('같은 설정으로 다시 누르면 원본 세션 id 로 재도전한다', async () => {
    renderPanel('INTERRUPTED')

    await userEvent.click(screen.getByRole('button', { name: '같은 설정으로 다시' }))

    expect(retryMutate).toHaveBeenCalledWith(7)
  })

  // 자료 수를 넘겨야 "삭제된 자료 N개 제외" 안내가 가능하다.
  it('원본 세션의 자료 수를 재도전 훅에 넘긴다', () => {
    render(
      <MemoryRouter>
        <SessionEndedPanel
          status="COMPLETED"
          sessionId={7}
          session={{ id: 7, contextDocumentIds: [1, 2, 3] }}
        />
      </MemoryRouter>,
    )

    expect(lastSourceCount).toHaveBeenCalledWith(3)
  })
})
