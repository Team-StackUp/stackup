import type { ReactNode } from 'react'
import { Eyebrow } from '../Eyebrow'
import { Heading } from '../Heading'

export type PageHeaderProps = {
  /** 모노 라벨. 화면의 성격을 한 단어로 — 'HISTORY', '피드백' 처럼. */
  eyebrow?: ReactNode
  title: ReactNode
  description?: ReactNode
  /** 우측 액션(버튼·링크). 제목과 같은 줄, 좁은 화면에서는 아래로 흐른다. */
  actions?: ReactNode
  /** 제목 위에 놓는 뒤로가기 링크 등. 라우팅 의존을 피해 노드로 받는다. */
  above?: ReactNode
  /**
   * 화면 제목이 아닌 섹션 제목으로 쓸 때.
   * 한 화면 안에서 헤더가 연달아 나오면(페이지 제목 → 섹션 제목) 두 단계 내려 쓴다 —
   * 랜딩은 섹션 사이에 큰 콘텐츠가 끼지만 앱 화면은 헤더가 바로 붙어 위계가 납작해진다.
   */
  level?: 'page' | 'section' | 'sub'
  className?: string
}

/**
 * 화면 상단 제목 블록.
 *
 * 랜딩이 섹션을 여는 방식(모노 라벨 → 큰 제목 → 한 줄 설명 → 헤어라인)을 그대로
 * 앱 화면에 옮긴 것. 화면마다 제목 크기·굵기가 제각각이던 것을 여기로 모은다.
 */
export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  above,
  level = 'page',
  className = '',
}: PageHeaderProps) {
  return (
    <header className={`border-b border-border ${level === 'sub' ? 'pb-4' : 'pb-6'} ${className}`}>
      {above ? <div className="mb-4">{above}</div> : null}
      <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
        <div className="min-w-0">
          {eyebrow ? (
            <Eyebrow className={level === 'sub' ? 'mb-1.5' : 'mb-2.5'}>{eyebrow}</Eyebrow>
          ) : null}
          <Heading level={level} as={level === 'page' ? 'h1' : 'h2'}>
            {title}
          </Heading>
          {description ? (
            <p
              className={`max-w-prose font-normal text-fg-muted ${
                level === 'sub' ? 'mt-1.5 text-caption' : 'mt-3 text-body'
              }`}
              style={{ wordBreak: 'keep-all' }}
            >
              {description}
            </p>
          ) : null}
        </div>
        {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  )
}
