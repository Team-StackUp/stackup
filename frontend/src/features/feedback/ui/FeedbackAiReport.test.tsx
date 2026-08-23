import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ReactNode } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiClient } from '@/shared/api'
import { FeedbackAiReport } from './FeedbackAiReport'

vi.mock('@/shared/api', async (importOriginal) => {
  const mod = await importOriginal<typeof import('@/shared/api')>()
  return { ...mod, apiClient: { get: vi.fn() } }
})

function renderWithQuery(ui: ReactNode) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

beforeEach(() => {
  vi.mocked(apiClient.get).mockReset()
})

describe('FeedbackAiReport', () => {
  it('reportFilePath 가 없으면(저장 실패 폴백·구버전·공개 응답) 아무것도 렌더하지 않는다', () => {
    const { container } = renderWithQuery(
      <FeedbackAiReport sessionId={50} reportFilePath={null} />,
    )
    expect(container).toBeEmptyDOMElement()
    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('보기 클릭 시에만 프록시를 호출하고 마크다운으로 렌더한다', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: '# 면접 피드백 리포트\n\n**핵심** 정리',
    } as never)
    renderWithQuery(
      <FeedbackAiReport sessionId={50} reportFilePath="feedback/50/report.md" />,
    )

    // 열람 전에는 fetch 하지 않는다.
    expect(apiClient.get).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'AI 학습 리포트 보기' }))
    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/sessions/50/feedback/report',
      expect.objectContaining({ responseType: 'text' }),
    )
    // 마크다운 렌더러(lazy)가 풀리면 원문 기호 없이 실제 엘리먼트로 보인다.
    const strong = await screen.findByText('핵심')
    expect(strong.tagName).toBe('STRONG')
    expect(screen.getByRole('button', { name: '.md 다운로드' })).toBeInTheDocument()
  })

  it('프록시 실패 시 재시도 가능한 에러 상태를 보여준다', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('boom'))
    renderWithQuery(
      <FeedbackAiReport sessionId={50} reportFilePath="feedback/50/report.md" />,
    )

    await userEvent.click(screen.getByRole('button', { name: 'AI 학습 리포트 보기' }))
    await waitFor(() =>
      expect(
        screen.getByText('AI 학습 리포트를 불러오지 못했습니다.'),
      ).toBeInTheDocument(),
    )
  })
})
