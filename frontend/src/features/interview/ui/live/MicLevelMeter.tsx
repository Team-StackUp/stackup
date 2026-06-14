import { useEffect, useRef } from 'react'

const BARS = 5

// 녹음 중 마이크 입력을 실시간 막대로 보여주는 레벨 미터.
// AnalyserNode 의 주파수 데이터를 BARS 개 밴드로 묶어 각 막대 높이에 반영한다.
// 프레임마다 리렌더하지 않도록 ref 로 DOM transform 을 직접 갱신한다.
export function MicLevelMeter({ stream }: { stream: MediaStream | null }) {
  const barRefs = useRef<(HTMLSpanElement | null)[]>([])

  useEffect(() => {
    if (!stream || typeof AudioContext === 'undefined') return
    const ctx = new AudioContext()
    void ctx.resume()
    const source = ctx.createMediaStreamSource(stream)
    const analyser = ctx.createAnalyser()
    analyser.fftSize = 64
    analyser.smoothingTimeConstant = 0.7
    source.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)
    const band = Math.max(1, Math.floor(data.length / BARS))

    let raf = 0
    const tick = () => {
      analyser.getByteFrequencyData(data)
      for (let i = 0; i < BARS; i++) {
        let sum = 0
        for (let j = 0; j < band; j++) sum += data[i * band + j]
        const level = sum / band / 255 // 0..1
        const el = barRefs.current[i]
        if (el) el.style.transform = `scaleY(${Math.max(0.18, Math.min(1, level * 1.8))})`
      }
      raf = requestAnimationFrame(tick)
    }
    tick()

    return () => {
      cancelAnimationFrame(raf)
      source.disconnect()
      void ctx.close()
    }
  }, [stream])

  return (
    <span className="flex h-6 items-center gap-[3px]" aria-hidden>
      {Array.from({ length: BARS }).map((_, i) => (
        <span
          key={i}
          ref={(el) => {
            barRefs.current[i] = el
          }}
          className="h-full w-1 origin-center rounded-full bg-danger/70 transition-transform duration-75"
          style={{ transform: 'scaleY(0.18)' }}
        />
      ))}
    </span>
  )
}
