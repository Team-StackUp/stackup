import { useEffect, useRef, useState, type ReactNode } from 'react'

export type RevealProps = {
  children: ReactNode
  /** 뷰포트 진입 후 지연(ms). 같은 블록 내 요소를 순차 등장시킬 때만 쓴다(최대 3~4단계). */
  delayMs?: number
  className?: string
  /** 감싸는 태그. 레이아웃(grid item 등)을 깨지 않게 필요 시 교체. */
  as?: 'div' | 'li' | 'section'
}

/**
 * 뷰포트에 들어올 때 한 번만 페이드업.
 *
 * 랜딩 섹션의 등장 모션 전용 — 스크롤을 따라 내용이 "도착하는" 정도의 절제된 모션만
 * 준다(이동 12px, 320ms). 한 번 보이면 다시 숨기지 않는다(스크롤 되돌릴 때 깜빡임 방지).
 *
 * `prefers-reduced-motion` 이면 관찰 없이 즉시 표시한다 — global.css 의 duration 무력화만으로는
 * 초기 opacity:0 이 남아 내용이 안 보일 수 있어서, 여기서 직접 분기한다.
 */
/** 관찰 없이 처음부터 보여줄 상황인지 — 모션 축소 선호 또는 IO 미지원. */
function shouldSkipObserving() {
  if (typeof IntersectionObserver === 'undefined') return true
  if (typeof window === 'undefined') return false
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

export function Reveal({ children, delayMs = 0, className = '', as = 'div' }: RevealProps) {
  const ref = useRef<HTMLElement | null>(null)
  // 초기값으로 결정한다 — effect 안에서 setState 하면 불필요한 연쇄 렌더가 생긴다.
  const [shown, setShown] = useState(shouldSkipObserving)

  useEffect(() => {
    if (shown) return

    const el = ref.current
    if (!el) return

    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setShown(true)
          io.disconnect()
        }
      },
      // 살짝 올라온 시점에 시작해 "이미 보이는데 뒤늦게 뜨는" 느낌을 없앤다.
      { rootMargin: '0px 0px -12% 0px', threshold: 0.01 },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [shown])

  const Tag = as

  return (
    <Tag
      ref={ref as never}
      className={className}
      style={{
        opacity: shown ? 1 : 0,
        transform: shown ? 'none' : 'translateY(12px)',
        transition: `opacity var(--duration-slow) var(--ease-decelerate) ${delayMs}ms, transform var(--duration-slow) var(--ease-decelerate) ${delayMs}ms`,
      }}
    >
      {children}
    </Tag>
  )
}
