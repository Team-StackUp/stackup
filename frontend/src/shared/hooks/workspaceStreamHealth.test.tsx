import { describe, it, expect, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import {
  workspaceStreamHealth,
  useAnalysisFallbackPolling,
  useWorkspaceStreamHealth,
} from './workspaceStreamHealth'

afterEach(() => {
  workspaceStreamHealth.set('idle')
})

describe('workspaceStreamHealth', () => {
  it('스트림이 죽었을 때만 폴백 폴링(5s)을 켠다', () => {
    const { result } = renderHook(() => useAnalysisFallbackPolling())
    expect(result.current).toBe(false)

    act(() => workspaceStreamHealth.set('down'))
    expect(result.current).toBe(5_000)

    act(() => workspaceStreamHealth.set('up'))
    expect(result.current).toBe(false)
  })

  it('idle(워크스페이스 밖)에서는 폴링하지 않는다', () => {
    const { result } = renderHook(() => useWorkspaceStreamHealth())
    expect(result.current).toBe('idle')
    const polling = renderHook(() => useAnalysisFallbackPolling())
    expect(polling.result.current).toBe(false)
  })
})
