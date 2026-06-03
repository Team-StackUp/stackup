import { useState } from 'react'

// 텍스트를 클립보드로 복사하고 2초간 copied 상태를 유지.
export function useCopyToClipboard(resetMs = 2000) {
  const [copied, setCopied] = useState(false)

  const copy = async (text: string): Promise<boolean> => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), resetMs)
      return true
    } catch {
      setCopied(false)
      return false
    }
  }

  return { copy, copied }
}
