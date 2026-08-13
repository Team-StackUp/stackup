import type { ReactNode } from 'react'

export type PanelProps = {
  children: ReactNode
  /** 안쪽 여백. sm — 밀집 리스트 / md — 기본 / lg — 단독 블록 */
  padding?: 'none' | 'sm' | 'md' | 'lg'
  /** 페이지 배경이 `bg-surface-raised` 인 구간에서는 한 단계 낮춰 대비를 만든다. */
  tone?: 'raised' | 'sunken'
  /** hover 시 보더만 진해지는 인터랙티브 표면(링크·버튼으로 감쌀 때). */
  interactive?: boolean
  as?: 'div' | 'li' | 'section' | 'article'
  className?: string
}

const paddingClass = {
  none: '',
  sm: 'px-4 py-3.5',
  md: 'p-5',
  lg: 'p-6 lg:p-8',
} as const

/**
 * 헤어라인 표면. 랜딩 `HomeSteps` 의 `Panel` 을 공용으로 끌어올린 것.
 *
 * 그림자를 쓰지 않는다 — 내용 표면의 깊이는 보더 한 줄과 배경 단계로만 준다.
 * elevation 은 실제로 떠 있는 것(모달, 히어로의 미리보기 목업)에만 남겨 둔다.
 * 목록 카드마다 그림자가 붙으면 무엇이 떠 있는지가 의미를 잃는다.
 */
export function Panel({
  children,
  padding = 'md',
  tone = 'raised',
  interactive = false,
  as = 'div',
  className = '',
}: PanelProps) {
  const Tag = as

  return (
    <Tag
      className={[
        'rounded-xl border border-border',
        tone === 'raised' ? 'bg-surface-raised' : 'bg-surface',
        paddingClass[padding],
        interactive
          ? 'transition-colors duration-fast hover:border-border-strong'
          : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </Tag>
  )
}
