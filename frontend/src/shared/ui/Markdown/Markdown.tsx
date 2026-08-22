import { Suspense, lazy } from 'react'
import type { MarkdownProps } from './Markdown.types'

// 렌더러(react-markdown 계열)는 무거우므로 사용 시점에 lazy 로드 — 메인 청크에 얹지 않는다
// (downloadPdf 의 jspdf 선례). 로딩 사이에는 종전과 동일한 plain text 로 보여 깜빡임이 없다.
const MarkdownContent = lazy(() => import('./MarkdownContent'))

export function Markdown({ children, className }: MarkdownProps) {
  return (
    <Suspense
      fallback={
        <p className={['whitespace-pre-wrap text-body leading-relaxed', className ?? ''].join(' ')}>
          {children}
        </p>
      }
    >
      <MarkdownContent className={className}>{children}</MarkdownContent>
    </Suspense>
  )
}
