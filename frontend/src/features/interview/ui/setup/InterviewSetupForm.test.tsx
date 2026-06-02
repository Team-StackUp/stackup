import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InterviewSetupForm } from './InterviewSetupForm'

describe('InterviewSetupForm', () => {
  it('모드·직군 선택 전에는 생성 버튼이 비활성', () => {
    render(<InterviewSetupForm documents={[]} onCreate={vi.fn()} />)
    expect(screen.getByRole('button', { name: '면접 생성' })).toBeDisabled()
  })

  it('모드·직군 선택 후 생성하면 요청을 만든다', async () => {
    const onCreate = vi.fn()
    render(<InterviewSetupForm documents={[]} onCreate={onCreate} />)
    await userEvent.click(screen.getByRole('radio', { name: '기술 면접' }))
    await userEvent.click(screen.getByRole('radio', { name: '백엔드' }))
    await userEvent.click(screen.getByRole('button', { name: '면접 생성' }))
    expect(onCreate).toHaveBeenCalledWith({
      mode: 'TECHNICAL',
      jobCategory: 'BACKEND',
      maxQuestions: 5,
      contextDocumentIds: [],
    })
  })
})
