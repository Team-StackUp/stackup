import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ConnectionBanner } from './ConnectionBanner'

describe('ConnectionBanner', () => {
  it('open 이면 그리지 않는다', () => {
    const { container } = render(<ConnectionBanner connection="open" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('closed 면 재연결 문구를 alert 로 알린다', () => {
    render(<ConnectionBanner connection="closed" />)
    expect(screen.getByRole('alert')).toHaveTextContent('연결이 끊겨 재연결 중입니다…')
  })

  it('문구를 맥락에 맞게 바꿀 수 있다', () => {
    render(
      <ConnectionBanner connection="closed" reconnectingText="실시간 연결이 끊겼습니다." />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('실시간 연결이 끊겼습니다.')
  })
})
