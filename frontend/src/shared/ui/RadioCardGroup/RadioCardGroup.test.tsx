import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RadioCardGroup } from './RadioCardGroup'

const options = [
  { value: 'a' as const, label: '기술 면접', description: '실무 기술·CS 위주' },
  { value: 'b' as const, label: '인성 면접', description: '경험·태도·협업' },
  { value: 'c' as const, label: '종합 면접' },
]

function setup(value: 'a' | 'b' | 'c' | null = null) {
  const onChange = vi.fn()
  render(
    <RadioCardGroup options={options} value={value} onChange={onChange} ariaLabel="면접 모드" />,
  )
  return { onChange }
}

describe('RadioCardGroup', () => {
  it('접근성 이름은 제목만 — 설명은 describedby 로 분리한다', () => {
    setup()
    // 설명까지 이름에 섞이면 "기술 면접실무 기술·CS 위주" 가 되어 스크린리더가 매번 되풀이한다.
    const radio = screen.getByRole('radio', { name: '기술 면접' })
    expect(radio.getAttribute('aria-describedby')).toBeTruthy()
  })

  it('화살표 키로 다음 항목을 선택한다 — radiogroup 규약', async () => {
    const { onChange } = setup('a')
    const first = screen.getByRole('radio', { name: '기술 면접' })
    first.focus()

    await userEvent.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenCalledWith('b')
  })

  it('마지막에서 오른쪽 화살표는 처음으로 돌아온다', async () => {
    const { onChange } = setup('c')
    screen.getByRole('radio', { name: '종합 면접' }).focus()

    await userEvent.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  it('선택 항목만 탭 정지점을 갖는다 (roving tabindex)', () => {
    setup('b')
    expect(screen.getByRole('radio', { name: '인성 면접' }).getAttribute('tabindex')).toBe('0')
    expect(screen.getByRole('radio', { name: '기술 면접' }).getAttribute('tabindex')).toBe('-1')
  })
})
