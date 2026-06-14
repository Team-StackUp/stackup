import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastViewport } from './ToastViewport'
import { toast, getToasts, dismissToast } from './toastStore'

afterEach(() => {
  // 스토어는 모듈 싱글톤 — 테스트 간 잔여 토스트 정리
  for (const t of getToasts()) dismissToast(t.id)
})

describe('toastStore', () => {
  it('push 하면 항목이 쌓이고 dismiss 하면 제거된다', () => {
    const id = toast.success('저장됐어요', 0)
    expect(getToasts().map((t) => t.message)).toContain('저장됐어요')
    dismissToast(id)
    expect(getToasts()).toHaveLength(0)
  })

  it('tone 별 헬퍼가 올바른 tone 을 단다', () => {
    toast.error('실패', 0)
    expect(getToasts().at(-1)?.tone).toBe('error')
  })
})

describe('ToastViewport', () => {
  it('토스트 메시지를 렌더하고 닫기 버튼으로 제거한다', async () => {
    render(<ToastViewport />)
    act(() => {
      toast.info('알림입니다', 0)
    })
    expect(screen.getByText('알림입니다')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '알림 닫기' }))
    expect(screen.queryByText('알림입니다')).toBeNull()
  })

  it('status 역할로 노출되어 스크린리더가 읽는다', () => {
    render(<ToastViewport />)
    act(() => {
      toast.success('완료', 0)
    })
    expect(screen.getByRole('status')).toBeTruthy()
  })
})
