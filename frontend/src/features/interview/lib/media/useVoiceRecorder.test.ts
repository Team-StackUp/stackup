import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useVoiceRecorder } from './useVoiceRecorder'

// 최소 MediaRecorder 스텁 — 가드가 깨져 늦은 스트림으로 녹음이 시작되면
// 여기서 start() 가 불려 테스트가 그 사실을 잡을 수 있다.
class FakeMediaRecorder {
  static isTypeSupported() {
    return true
  }
  state = 'inactive'
  ondataavailable: unknown = null
  onstop: unknown = null
  start() {
    this.state = 'recording'
  }
  stop() {
    this.state = 'inactive'
  }
}

function installMediaRecorder() {
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('useVoiceRecorder', () => {
  it('권한 대기 중 언마운트되면 뒤늦게 도착한 트랙을 정지하고 녹음을 시작하지 않는다', async () => {
    installMediaRecorder()
    const stop = vi.fn()
    const track = { stop }
    let resolveStream!: (s: unknown) => void
    const pending = new Promise((res) => {
      resolveStream = res
    })
    const getUserMedia = vi.fn().mockReturnValue(pending)
    Object.defineProperty(navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
    })
    const startSpy = vi.spyOn(FakeMediaRecorder.prototype, 'start')

    const { result, unmount } = renderHook(() => useVoiceRecorder())

    // 권한 프롬프트 대기(getUserMedia 미해결) 중 start 호출.
    let startPromise!: Promise<boolean>
    act(() => {
      startPromise = result.current.start()
    })
    expect(result.current.status).toBe('requesting')

    // 대기 중 언마운트 → 뒤늦게 권한 허용.
    unmount()
    await act(async () => {
      resolveStream({ getTracks: () => [track] })
      await startPromise
    })

    // 소유자 없는 트랙은 즉시 정지되고, 녹음(MediaRecorder.start)은 시작되지 않아야 한다.
    expect(stop).toHaveBeenCalled()
    expect(startSpy).not.toHaveBeenCalled()
  })
})
