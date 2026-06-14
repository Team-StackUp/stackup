import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmDialog } from './ConfirmDialog'

const base = {
  title: '삭제하시겠습니까?',
  description: '되돌릴 수 없습니다.',
  onConfirm: () => {},
  onCancel: () => {},
}

describe('ConfirmDialog', () => {
  it('open=false면 렌더되지 않는다', () => {
    render(<ConfirmDialog {...base} open={false} />)
    expect(screen.queryByText('삭제하시겠습니까?')).toBeNull()
  })

  it('open이면 제목·설명을 보여준다', () => {
    render(<ConfirmDialog {...base} open />)
    expect(screen.getByText('삭제하시겠습니까?')).toBeTruthy()
    expect(screen.getByText('되돌릴 수 없습니다.')).toBeTruthy()
  })

  it('확인/취소 버튼이 각각 콜백을 호출한다', async () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        {...base}
        open
        confirmLabel="삭제"
        cancelLabel="취소"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('loading이면 취소가 비활성화되고 확인은 막힌다', async () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog
        {...base}
        open
        loading
        confirmLabel="삭제"
        cancelLabel="취소"
        onConfirm={onConfirm}
      />,
    )
    expect(screen.getByRole('button', { name: '취소' })).toBeDisabled()
    // loading 시 Spinner가 접근성 이름에 더해질 수 있어 부분 일치로 찾는다.
    const confirm = screen.getByRole('button', { name: /삭제/ })
    expect(confirm).toBeDisabled()
    await userEvent.click(confirm)
    expect(onConfirm).not.toHaveBeenCalled()
  })
})
