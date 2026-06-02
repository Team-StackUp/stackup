import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'

describe('Button', () => {
  it('클릭 시 onClick 호출', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>확인</Button>)
    await userEvent.click(screen.getByRole('button', { name: '확인' }))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('loading이면 비활성화되어 클릭이 막힌다', async () => {
    const onClick = vi.fn()
    render(<Button loading onClick={onClick}>제출</Button>)
    const btn = screen.getByRole('button')
    expect(btn).toBeDisabled()
    await userEvent.click(btn)
    expect(onClick).not.toHaveBeenCalled()
  })
})
