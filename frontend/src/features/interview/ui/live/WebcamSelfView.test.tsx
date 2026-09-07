import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { WebcamSelfView } from './WebcamSelfView'

function mockMediaDevices(getUserMedia: ReturnType<typeof vi.fn>) {
  Object.defineProperty(navigator, 'mediaDevices', {
    value: { getUserMedia },
    configurable: true,
  })
}

describe('WebcamSelfView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('켜기 클릭 시 스트림을 받아 live 가 되고, 끄기 클릭 시 트랙을 정지한다', async () => {
    const stop = vi.fn()
    const stream = { getTracks: () => [{ stop }] }
    mockMediaDevices(vi.fn().mockResolvedValue(stream))

    render(<WebcamSelfView />)
    await userEvent.click(screen.getByRole('button', { name: '카메라 켜기' }))

    const off = await screen.findByRole('button', { name: '카메라 끄기' })
    await userEvent.click(off)
    expect(stop).toHaveBeenCalled()
  })

  it('권한 거부 시 안내를 보여준다', async () => {
    mockMediaDevices(vi.fn().mockRejectedValue(new Error('denied')))

    render(<WebcamSelfView />)
    await userEvent.click(screen.getByRole('button', { name: '카메라 켜기' }))

    expect(await screen.findByText('권한 거부됨')).toBeInTheDocument()
  })

  it('언마운트 시 트랙을 정지한다', async () => {
    const stop = vi.fn()
    const stream = { getTracks: () => [{ stop }] }
    mockMediaDevices(vi.fn().mockResolvedValue(stream))

    const { unmount } = render(<WebcamSelfView />)
    await userEvent.click(screen.getByRole('button', { name: '카메라 켜기' }))
    await screen.findByRole('button', { name: '카메라 끄기' })

    unmount()
    expect(stop).toHaveBeenCalled()
  })

  it('권한 대기 중 언마운트되면 뒤늦게 도착한 스트림도 정지한다 (LED 잔존 방지)', async () => {
    const stop = vi.fn()
    const stream = { getTracks: () => [{ stop }] }
    let resolveStream!: (s: typeof stream) => void
    const pending = new Promise<typeof stream>((res) => {
      resolveStream = res
    })
    mockMediaDevices(vi.fn().mockReturnValue(pending))

    const { unmount } = render(<WebcamSelfView />)
    await userEvent.click(screen.getByRole('button', { name: '카메라 켜기' }))
    // 프롬프트 대기 중(getUserMedia 미해결) 상태에서 언마운트.
    unmount()
    // 권한이 뒤늦게 허용됨 → 소유자 없는 트랙은 즉시 정지되어야 한다.
    resolveStream(stream)
    await pending
    expect(stop).toHaveBeenCalled()
  })
})

beforeEach(() => {
  // jsdom 은 HTMLVideoElement.srcObject 를 구현하지 않으므로 setter 를 무해하게 stub.
  if (!Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'srcObject')) {
    Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', {
      configurable: true,
      get() {
        return null
      },
      set() {},
    })
  }
})
