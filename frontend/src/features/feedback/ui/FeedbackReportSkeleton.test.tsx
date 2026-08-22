import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { FeedbackReportSkeleton } from './FeedbackReportSkeleton'

afterEach(() => {
  vi.useRealTimers()
})

describe('FeedbackReportSkeleton', () => {
  it('생성 중 안내와 자동 표시 예고를 보여준다', () => {
    render(<FeedbackReportSkeleton />)
    expect(screen.getByRole('status')).toHaveTextContent('피드백을 생성하는 중입니다')
    expect(screen.getByRole('status')).toHaveTextContent('완성되면 자동으로 표시됩니다')
  })

  it('경과 시간이 흐른다 — 멈춘 화면으로 보이지 않게', () => {
    vi.useFakeTimers()
    render(<FeedbackReportSkeleton />)
    expect(screen.getByRole('status')).toHaveTextContent('(0s 경과)')
    act(() => {
      vi.advanceTimersByTime(12_000)
    })
    expect(screen.getByRole('status')).toHaveTextContent('(12s 경과)')
  })
  it('진행 이벤트가 오면 기본 안내 대신 진행 문구를 보여준다 (B2)', () => {
    render(
      <FeedbackReportSkeleton
        progress={{ message: '세부 평가를 진행하고 있어요. (3/5)', completed: 3, total: 5 }}
      />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('세부 평가를 진행하고 있어요. (3/5)')
    expect(screen.getByRole('status')).not.toHaveTextContent('보통 1분 내외')
  })

  it('진행 이벤트 미수신이면 기본 안내 문구를 유지한다', () => {
    render(<FeedbackReportSkeleton progress={null} />)
    expect(screen.getByRole('status')).toHaveTextContent('보통 1분 내외')
  })
})
