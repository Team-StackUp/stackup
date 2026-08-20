import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SmallScreenNotice } from './SmallScreenNotice'

const originalWidth = window.innerWidth

function setWidth(px: number) {
  Object.defineProperty(window, 'innerWidth', { value: px, configurable: true, writable: true })
}

beforeEach(() => window.localStorage.clear())
afterEach(() => setWidth(originalWidth))

describe('SmallScreenNotice', () => {
  it('좁은 화면에서 안내를 보여준다', () => {
    setWidth(375)

    render(<SmallScreenNotice />)

    expect(screen.getByText(/데스크탑 환경을 권장/)).toBeInTheDocument()
  })

  it('넓은 화면에서는 끼어들지 않는다', () => {
    setWidth(1280)

    render(<SmallScreenNotice />)

    expect(screen.queryByText(/데스크탑 환경을 권장/)).not.toBeInTheDocument()
  })

  // 매 진입마다 같은 안내가 뜨면 그게 더 방해가 된다.
  it('한 번 닫으면 다시 뜨지 않는다', async () => {
    setWidth(375)
    const { unmount } = render(<SmallScreenNotice />)

    await userEvent.click(screen.getByRole('button', { name: '안내 닫기' }))
    expect(screen.queryByText(/데스크탑 환경을 권장/)).not.toBeInTheDocument()
    unmount()

    render(<SmallScreenNotice />)
    expect(screen.queryByText(/데스크탑 환경을 권장/)).not.toBeInTheDocument()
  })
})
