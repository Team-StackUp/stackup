import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AccountView } from './AccountView'

const remove = vi.fn(async () => true)
const logout = vi.fn(async () => {})

vi.mock('@/features/auth', () => ({
  useAuth: () => ({
    user: { displayName: '홍길동', email: 'hong@example.com', avatarUrl: null },
    status: 'authenticated',
  }),
  useLogout: () => ({ logout, loggingOut: false }),
  useDeleteAccount: () => ({ remove, deleting: false }),
}))

describe('AccountView', () => {
  beforeEach(() => {
    // 모듈 스코프 mock 이라 테스트끼리 호출 횟수가 새어 나간다.
    remove.mockClear()
    logout.mockClear()
  })

  // 되돌릴 수 없는 액션이라 확인 다이얼로그를 거쳐야 한다 (docs/ui-patterns.md).
  it('탈퇴 버튼만 눌러서는 탈퇴되지 않는다', async () => {
    render(<AccountView />)

    await userEvent.click(screen.getByRole('button', { name: '회원 탈퇴' }))

    expect(remove).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('다이얼로그에서 확인해야 탈퇴가 실행된다', async () => {
    render(<AccountView />)

    await userEvent.click(screen.getByRole('button', { name: '회원 탈퇴' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '탈퇴하기' }))

    await waitFor(() => expect(remove).toHaveBeenCalledTimes(1))
  })

  it('취소하면 아무 일도 일어나지 않는다', async () => {
    render(<AccountView />)

    await userEvent.click(screen.getByRole('button', { name: '회원 탈퇴' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '취소' }))

    expect(remove).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  // 우리가 지우는 건 우리가 가진 사본뿐이다 — GitHub 앱 승인 해제 경로를 알려주지 않으면
  // 사용자는 권한이 남아 있다는 사실 자체를 모른다.
  it('GitHub 앱 권한 해제 경로를 안내한다', () => {
    render(<AccountView />)

    expect(screen.getByRole('link', { name: /Authorized OAuth Apps/ })).toHaveAttribute(
      'href',
      'https://github.com/settings/applications',
    )
  })

  it('탈퇴로 무엇이 사라지는지 미리 알려준다', () => {
    render(<AccountView />)

    expect(screen.getByText(/공유 링크가 즉시 만료/)).toBeInTheDocument()
    expect(screen.getByText(/GitHub 접근 권한\(access token\)을 즉시 폐기/)).toBeInTheDocument()
    expect(screen.getByText(/이전 데이터는 복구되지 않습니다/)).toBeInTheDocument()
  })
})
