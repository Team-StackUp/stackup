import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { AuthUser } from '@/features/auth'
import { ReposView } from './ReposView'

// 레포 목록·등록 위젯은 서버 호출을 하므로, 이 테스트에서는 "렌더됐는지"만 본다.
vi.mock('@/features/repo', () => ({
  RepoList: () => <div data-testid="repo-list" />,
  RepoPicker: () => <div data-testid="repo-picker" />,
}))
vi.mock('@/features/analysis', () => ({
  DocumentList: () => <div data-testid="document-list" />,
}))

const mockUser = vi.fn<() => AuthUser | null>()
vi.mock('@/features/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/features/auth')>()),
  useAuth: () => ({ user: mockUser(), status: 'authenticated' }),
}))

function renderView() {
  return render(
    <MemoryRouter>
      <ReposView />
    </MemoryRouter>,
  )
}

const githubUser: AuthUser = {
  id: 1,
  provider: 'GITHUB',
  displayName: 'octocat',
  githubId: 123,
  githubUsername: 'octocat',
  email: null,
  avatarUrl: null,
}

const googleUser: AuthUser = {
  id: 2,
  provider: 'GOOGLE',
  displayName: '홍길동',
  githubId: null,
  githubUsername: null,
  email: 'hong@example.com',
  avatarUrl: null,
}

describe('ReposView — provider 별 분기', () => {
  it('GitHub 계정은 레포 등록·목록·분석 결과를 보여준다', () => {
    mockUser.mockReturnValue(githubUser)
    renderView()

    expect(screen.getByTestId('repo-picker')).toBeTruthy()
    expect(screen.getByTestId('repo-list')).toBeTruthy()
    expect(screen.getByTestId('document-list')).toBeTruthy()
  })

  it('Google 계정은 조회를 시도하지 않고 안내와 대체 경로를 보여준다', () => {
    mockUser.mockReturnValue(googleUser)
    renderView()

    // 목록 위젯이 아예 렌더되지 않아야 한다 — 렌더되면 409 만 받는 요청이 나가고,
    // 빈 목록이 "레포가 없다"는 잘못된 사실로 읽힌다.
    expect(screen.queryByTestId('repo-picker')).toBeNull()
    expect(screen.queryByTestId('repo-list')).toBeNull()
    expect(screen.queryByTestId('document-list')).toBeNull()

    expect(screen.getByText('GitHub 계정에서 쓸 수 있는 기능이에요')).toBeTruthy()
    const cta = screen.getByRole('link', { name: '이력서 올리러 가기' })
    expect(cta.getAttribute('href')).toBe('/workspace/resumes')
  })
})
