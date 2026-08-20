import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useNoIndex } from './useNoIndex'

const robotsMeta = () => document.head.querySelector('meta[name="robots"]')

describe('useNoIndex', () => {
  it('마운트 동안 robots noindex 를 심는다', () => {
    renderHook(() => useNoIndex())
    expect(robotsMeta()?.getAttribute('content')).toContain('noindex')
  })

  // 다른 페이지로 이동했는데 태그가 남으면 앱 전체가 색인에서 빠진다.
  it('언마운트하면 걷어낸다', () => {
    const { unmount } = renderHook(() => useNoIndex())
    unmount()
    expect(robotsMeta()).toBeNull()
  })
})
