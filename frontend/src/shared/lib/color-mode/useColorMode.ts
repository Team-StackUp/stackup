import { useCallback, useEffect, useState } from 'react'
import {
  applyColorMode,
  isDarkApplied,
  readStoredColorMode,
  storeColorMode,
  subscribeSystemScheme,
  type ColorMode,
} from './colorMode'

export type UseColorModeResult = {
  mode: ColorMode
  /** 지금 화면에 실제로 다크가 적용됐는지(mode=system 이면 OS 설정에 따라 갈린다). */
  isDark: boolean
  setMode: (mode: ColorMode) => void
  /** 라이트 ↔ 다크 즉시 전환. system 을 벗어나 명시 모드로 고정한다. */
  toggle: () => void
}

/**
 * 컬러 모드 상태. `<html>` 속성이 진실의 원천이고 훅은 그걸 비추기만 한다
 * (여러 곳에서 동시에 써도 어긋나지 않게).
 */
export function useColorMode(): UseColorModeResult {
  const [mode, setModeState] = useState<ColorMode>(readStoredColorMode)
  const [isDark, setIsDark] = useState(isDarkApplied)

  // mode=system 일 때 OS 설정 변화를 따라간다.
  useEffect(() => {
    if (mode !== 'system') return
    return subscribeSystemScheme(() => {
      applyColorMode('system')
      setIsDark(isDarkApplied())
    })
  }, [mode])

  const setMode = useCallback((next: ColorMode) => {
    applyColorMode(next)
    storeColorMode(next)
    setModeState(next)
    setIsDark(isDarkApplied())
  }, [])

  const toggle = useCallback(() => {
    setMode(isDarkApplied() ? 'light-only' : 'dark-only')
  }, [setMode])

  return { mode, isDark, setMode, toggle }
}
