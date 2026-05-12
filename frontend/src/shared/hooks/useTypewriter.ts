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
    let intervalId: number | undefined
    const timeoutId = window.setTimeout(() => {
      let i = 0
      intervalId = window.setInterval(() => {
        i += 1
        setTyped(text.slice(0, i))
        if (i >= text.length && intervalId !== undefined) {
          window.clearInterval(intervalId)
        }
      }, stepMs)
    }, startDelayMs)

    return () => {
      window.clearTimeout(timeoutId)
      if (intervalId !== undefined) window.clearInterval(intervalId)
    }
  }, [text, startDelayMs, stepMs, respectReducedMotion])

  return { typed, done: typed.length >= text.length }
}
