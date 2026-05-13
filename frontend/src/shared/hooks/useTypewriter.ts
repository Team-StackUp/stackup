import { useEffect, useState } from 'react'

export type UseTypewriterOptions = {
  startDelayMs?: number
  stepMs?: number
  respectReducedMotion?: boolean
}

export function useTypewriter(
  text: string,
  options: UseTypewriterOptions = {},
) {
  const {
    startDelayMs = 0,
    stepMs = 100,
    respectReducedMotion = true,
  } = options

  const [typed, setTyped] = useState('')

  useEffect(() => {
    const reduce =
      respectReducedMotion &&
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

    if (reduce) {
      setTyped(text)
      return
    }

    setTyped('')
    let rafId = 0
    let startTime: number | null = null

    const tick = (now: number) => {
      if (startTime === null) startTime = now
      const elapsed = now - startTime - startDelayMs
      if (elapsed < 0) {
        rafId = window.requestAnimationFrame(tick)
        return
      }
      const next = Math.min(
        Math.floor(elapsed / stepMs) + 1,
        text.length,
      )
      setTyped(text.slice(0, next))
      if (next < text.length) {
        rafId = window.requestAnimationFrame(tick)
      }
    }

    rafId = window.requestAnimationFrame(tick)

    return () => {
      window.cancelAnimationFrame(rafId)
    }
  }, [text, startDelayMs, stepMs, respectReducedMotion])

  return { typed, done: typed.length >= text.length }
}
