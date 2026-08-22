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
})
