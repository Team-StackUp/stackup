import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('제목과 설명을 보여준다', () => {
    render(<EmptyState title="아직 없어요" description="추가해보세요" />)
    expect(screen.getByText('아직 없어요')).toBeTruthy()
    expect(screen.getByText('추가해보세요')).toBeTruthy()
  })

  it('description 없으면 제목만 렌더한다', () => {
    render(<EmptyState title="비었음" />)
    expect(screen.getByText('비었음')).toBeTruthy()
  })

  it('action 영역(CTA)을 렌더한다', () => {
    render(<EmptyState title="비었음" action={<button type="button">시작</button>} />)
    expect(screen.getByRole('button', { name: '시작' })).toBeTruthy()
  })
})
