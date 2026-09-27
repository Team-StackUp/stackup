import { afterEach, describe, expect, it, vi } from 'vitest'

import { withRefreshLock } from './client'

/**
 * refresh 는 서버에서 회전한다 — 기존 토큰을 즉시 revoke 하고 새 토큰을 발급한다.
 * client.ts 의 단일 비행(`refreshing`)은 모듈 변수라 **한 탭 안에서만** 듣는다.
 * 리프레시 쿠키는 탭이 공유하므로 탭 두 개가 동시에 부팅하면 진 쪽이 401 로 로그아웃된다.
 */
describe('withRefreshLock', () => {
  const original = Object.getOwnPropertyDescriptor(navigator, 'locks')

  afterEach(() => {
    if (original) Object.defineProperty(navigator, 'locks', original)
    else delete (navigator as { locks?: unknown }).locks
    vi.restoreAllMocks()
  })

  function stubLocks() {
    // 이름별로 직렬화하는 최소 구현. 실제 Web Locks 의 계약과 같다.
    const chains = new Map<string, Promise<unknown>>()
    const request = vi.fn(async (name: string, fn: () => Promise<unknown>) => {
      const prev = chains.get(name) ?? Promise.resolve()
      const next = prev.then(fn, fn)
      chains.set(
        name,
        next.then(
          () => undefined,
          () => undefined,
        ),
      )
      return next
    })
    Object.defineProperty(navigator, 'locks', {
      value: { request },
      configurable: true,
    })
    return request
  }

  it('락이 있으면 같은 이름으로 감싸 실행한다', async () => {
    const request = stubLocks()

    await expect(withRefreshLock(async () => 'token')).resolves.toBe('token')

    expect(request).toHaveBeenCalledTimes(1)
    expect(request.mock.calls[0][0]).toBe('stackup-auth-refresh')
  })

  it('동시에 들어온 갱신이 겹치지 않는다', async () => {
    stubLocks()
    let running = 0
    let maxConcurrent = 0
    const job = async () => {
      running += 1
      maxConcurrent = Math.max(maxConcurrent, running)
      await new Promise((r) => setTimeout(r, 5))
      running -= 1
      return 'ok'
    }

    await Promise.all([withRefreshLock(job), withRefreshLock(job), withRefreshLock(job)])

    // 이게 1이 아니면 탭들이 같은 리프레시 토큰으로 동시에 회전을 시도한다는 뜻이다.
    expect(maxConcurrent).toBe(1)
  })

  it('앞선 갱신이 실패해도 뒤 갱신이 막히지 않는다', async () => {
    stubLocks()
    const boom = withRefreshLock(async () => {
      throw new Error('refresh failed')
    })

    await expect(boom).rejects.toThrow('refresh failed')
    await expect(withRefreshLock(async () => 'next')).resolves.toBe('next')
  })

  // 구형 Safari·비보안 컨텍스트에는 Web Locks 가 없다. 그때도 갱신은 되어야 한다.
  it('락이 없으면 그대로 실행한다', async () => {
    delete (navigator as { locks?: unknown }).locks

    await expect(withRefreshLock(async () => 'token')).resolves.toBe('token')
  })
})
