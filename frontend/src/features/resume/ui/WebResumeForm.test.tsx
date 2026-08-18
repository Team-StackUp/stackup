import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WebResumeForm } from './WebResumeForm'

const mutate = vi.fn()
vi.mock('../model/useResumes', () => ({
  useRegisterWebResume: () => ({ mutate, isPending: false }),
}))

beforeEach(() => mutate.mockClear())

describe('WebResumeForm', () => {
  it('공개 URL을 등록하면 다듬은 값으로 요청한다', async () => {
    render(<WebResumeForm />)

    await userEvent.type(
      screen.getByLabelText('포트폴리오·블로그 링크'),
      '  https://my-portfolio.dev/about  ',
    )
    await userEvent.click(screen.getByRole('button', { name: '링크 등록' }))

    expect(mutate).toHaveBeenCalledTimes(1)
    expect(mutate.mock.calls[0][0]).toBe('https://my-portfolio.dev/about')
  })

  it('빈 입력은 서버에 보내지 않고 안내한다', async () => {
    render(<WebResumeForm />)

    await userEvent.click(screen.getByRole('button', { name: '링크 등록' }))

    expect(mutate).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('URL을 입력해 주세요.')
  })

  // 서버가 최종 판정하지만 명백한 실수는 왕복 없이 잡는다.
  it.each(['portfolio.dev', 'ftp://example.com/x'])(
    'http(s)가 아닌 %s 는 왕복 없이 거부한다',
    async (value) => {
      render(<WebResumeForm />)

      await userEvent.type(screen.getByLabelText('포트폴리오·블로그 링크'), value)
      await userEvent.click(screen.getByRole('button', { name: '링크 등록' }))

      expect(mutate).not.toHaveBeenCalled()
      expect(screen.getByRole('alert')).toBeInTheDocument()
    },
  )

  it('다시 입력하면 에러 메시지가 사라진다', async () => {
    render(<WebResumeForm />)
    const input = screen.getByLabelText('포트폴리오·블로그 링크')

    await userEvent.click(screen.getByRole('button', { name: '링크 등록' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()

    await userEvent.type(input, 'h')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(input).not.toHaveAttribute('aria-invalid')
  })
})
