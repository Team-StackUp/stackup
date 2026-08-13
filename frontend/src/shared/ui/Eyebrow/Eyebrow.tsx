import type { ReactNode } from 'react'

export type EyebrowProps = {
  children: ReactNode
  /** brand — 섹션이 브랜드 성격을 띨 때만. 기본은 중립(subtle). */
  tone?: 'subtle' | 'brand'
  /**
   * 이 라벨이 섹션의 **제목 역할**을 하면 heading 태그로 렌더한다.
   * 큰 제목 없이 모노 라벨만으로 섹션을 여는 자리(피드백 리포트의 '강점',
   * 히스토리의 '지난 면접')가 그렇다 — `<p>` 로 두면 문서 개요에서 사라져
   * 스크린리더로 섹션 간 이동이 불가능해진다.
   */
  as?: 'p' | 'h2' | 'h3'
  className?: string
}

/**
 * 섹션 위에 얹는 모노 라벨.
 *
 * 랜딩(`HomeSteps`, `HomeFeatures`)이 쓰는 형태를 그대로 표준화한 것 —
 * `uppercase tracking-[0.22em]` 같은 넓은 자간 라벨은 쓰지 않는다.
 * 자간을 벌리면 한글 라벨이 무너지고, 랜딩의 조밀한 모노 라벨과 톤이 어긋난다.
 */
export function Eyebrow({
  children,
  tone = 'subtle',
  as: Tag = 'p',
  className = '',
}: EyebrowProps) {
  return (
    <Tag
      className={`font-mono text-caption font-normal tracking-tight ${
        tone === 'brand' ? 'text-primary-fg' : 'text-fg-subtle'
      } ${className}`}
    >
      {children}
    </Tag>
  )
}
