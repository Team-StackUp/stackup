import type { ReactNode } from 'react'
import { PageHeader } from '@/shared/ui'

//단순 위젯 수준은 model분리를 추후에 분리를 고려합니다.
type Props = {
  title: string
  description?: string
  /** 제목 위 모노 라벨. 랜딩 섹션과 같은 리듬(라벨 → 제목 → 설명 → 헤어라인). */
  eyebrow?: string
  action?: ReactNode
  children: ReactNode
}

export function WorkspaceSection({
  title,
  description,
  eyebrow,
  action,
  children,
}: Props) {
  return (
    <section>
      <PageHeader
        level="sub"
        eyebrow={eyebrow}
        title={title}
        description={description}
        actions={action}
        className="mb-5"
      />
      {children}
    </section>
  )
}
