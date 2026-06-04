import { useEffect, useRef, useState } from 'react'

// 스트리밍 텍스트를 일정 속도로 점진 표시(타자기). enabled=false면 전체 즉시 반환.
// fullText 가 늘어나면 따라가고, 남은 양에 비례해 step 을 키워 길고 빠른 스트림은 지연 없이 따라잡는다.
export function useTypewriter(fullText: string, enabled: boolean): string {
  const [count, setCount] = useState(enabled ? 0 : fullText.length)
  const targetRef = useRef(fullText)
  targetRef.current = fullText

  useEffect(() => {
    if (!enabled) {
      setCount(targetRef.current.length)
      return
    }
    const id = setInterval(() => {
      setCount((c) => {
        const target = targetRef.current.length
        if (c >= target) return c
        const remaining = target - c
        const step = Math.max(2, Math.ceil(remaining / 6))
        return Math.min(target, c + step)
      })
    }, 35)
    return () => clearInterval(id)
  }, [enabled])

  const shown = enabled ? Math.min(count, fullText.length) : fullText.length
  return fullText.slice(0, shown)
}
