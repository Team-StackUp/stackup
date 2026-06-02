import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnswerComposer } from './AnswerComposer'

describe('AnswerComposer', () => {
  it('Enter로 제출하고 입력을 비운다', async () => {
    const onSubmit = vi.fn()
    render(<AnswerComposer onSubmit={onSubmit} />)
    const input = screen.getByLabelText('답변 입력')
    await userEvent.type(input, '내 답변{Enter}')
    expect(onSubmit).toHaveBeenCalledWith('내 답변')
    expect(input).toHaveValue('')
  })

  it('Shift+Enter는 제출하지 않는다', async () => {
    const onSubmit = vi.fn()
    render(<AnswerComposer onSubmit={onSubmit} />)
    const input = screen.getByLabelText('답변 입력')
    await userEvent.type(input, '줄1{Shift>}{Enter}{/Shift}')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('disabled면 제출되지 않는다', async () => {
    const onSubmit = vi.fn()
    render(<AnswerComposer disabled onSubmit={onSubmit} />)
    const input = screen.getByLabelText('답변 입력')
    await userEvent.type(input, '답변{Enter}')
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
