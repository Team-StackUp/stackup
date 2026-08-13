import type { ReactNode } from 'react'

export type HeadingLevel = 'display' | 'page' | 'section' | 'sub'
export type HeadingProps = {
  children: ReactNode
  /**
   * 시각적 크기. 문서 구조(h1~h4)와 분리되어 있으니 `as` 로 태그를 따로 지정한다.
   *  display — 랜딩 히어로 전용
   *  page    — 화면 제목
   *  section — 화면 안 섹션 제목
   *  sub     — 카드·블록 제목
   */
  level?: HeadingLevel
  as?: 'h1' | 'h2' | 'h3' | 'h4' | 'p' | 'span'
  className?: string
  id?: string
}

/**
 * 랜딩과 동일한 조판의 헤딩.
 *
 * 세 가지가 이 화면들의 인상을 만든다 —
 *  1. Pretendard(`font-sans`). global.css 는 h1~h3 에 Bricolage(`--font-heading`)를 걸지만
 *     랜딩은 전부 `font-sans` 로 덮어썼다. 한글이 섞인 제목에서 라틴 디스플레이 폰트는
 *     자소 높이가 튀어 조판이 흔들린다.
 *  2. 뷰포트 비례 `clamp()`. 고정 `text-h4` 스케일은 랜딩의 반응형 리듬과 어긋난다.
 *  3. 음수 자간 + `word-break: keep-all`. 한글 제목이 어절 중간에서 끊기지 않게 한다.
 */
const levelStyle: Record<HeadingLevel, React.CSSProperties> = {
  display: {
    fontSize: 'clamp(52px, 7.6vw, 104px)',
    lineHeight: 0.94,
    letterSpacing: '-0.055em',
  },
  page: {
    fontSize: 'clamp(28px, 3.4vw, 42px)',
    lineHeight: 1.06,
    letterSpacing: '-0.04em',
  },
  section: {
    fontSize: 'clamp(22px, 2.2vw, 30px)',
    lineHeight: 1.25,
    letterSpacing: '-0.03em',
  },
  sub: {
    fontSize: '18px',
    lineHeight: 1.4,
    letterSpacing: '-0.02em',
  },
}

export function Heading({
  children,
  level = 'section',
  as,
  className = '',
  id,
}: HeadingProps) {
  const Tag = as ?? (level === 'page' || level === 'display' ? 'h1' : 'h2')

  return (
    <Tag
      id={id}
      className={`font-sans font-bold text-fg ${className}`}
      style={{ ...levelStyle[level], wordBreak: 'keep-all' }}
    >
      {children}
    </Tag>
  )
}
