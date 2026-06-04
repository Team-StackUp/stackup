import { useCallback, useState } from 'react'

// 면접 중 질문 전달 방식.
//  - text : 텍스트로 읽는다. 음성은 자동재생하지 않고 "대기 중" 표시 + 수동 재생.
//  - voice: 음성으로 듣는다. 텍스트는 숨기고 자동재생.
export type DeliveryMode = 'text' | 'voice'

const STORAGE_KEY = 'stackup:interview:delivery-mode'

// 텍스트 읽는 도중 음성이 끼어드는 UX 를 막기 위해 기본값은 text.
function readStored(): DeliveryMode {
  if (typeof window === 'undefined') return 'text'
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'voice' ? 'voice' : 'text'
  } catch {
    return 'text'
  }
}

export function useDeliveryMode(): [DeliveryMode, (mode: DeliveryMode) => void] {
  const [mode, setMode] = useState<DeliveryMode>(readStored)

  const update = useCallback((next: DeliveryMode) => {
    setMode(next)
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // 저장 실패는 무시 — 세션 내 상태는 유지된다.
    }
  }, [])

  return [mode, update]
}
