import type { ComponentProps } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import type { MarkdownProps } from './Markdown.types'

// 렌더러 본체 — Markdown.tsx 가 lazy import 한다(메인 청크 비대화 방지, downloadPdf 의 jspdf 선례).
// sanitize: react-markdown 은 raw HTML 을 렌더하지 않지만(escape), rehype-sanitize 를 겹쳐
// 플러그인 추가 등 미래 변화에도 안전 기본값을 유지한다 (docs/security.md §4.4).

// react-markdown 은 components 에 hast `node` 를 넘긴다 — DOM 속성이 아니므로 걸러낸다.
function omitNode<T extends { node?: unknown }>(props: T): Omit<T, 'node'> {
  const rest = { ...props }
  delete (rest as { node?: unknown }).node
  return rest
}

// global.css 가 p/heading margin 을 0 으로 리셋하므로 수직 리듬은 여기서 직접 정의한다.
// 헤딩은 디스플레이 스케일(38px+)이 아니라 본문 카드 관례(text-[18px]/text-body bold 우회)를 따른다.
const components: ComponentProps<typeof ReactMarkdown>['components'] = {
  h1: (p) => (
    <h3 className="text-[18px] font-bold tracking-[-0.02em] text-fg-strong" {...omitNode(p)} />
  ),
  h2: (p) => (
    <h4 className="text-[18px] font-bold tracking-[-0.02em] text-fg-strong" {...omitNode(p)} />
  ),
  h3: (p) => <h5 className="text-body font-bold text-fg-strong" {...omitNode(p)} />,
  h4: (p) => <h6 className="text-body font-bold text-fg-strong" {...omitNode(p)} />,
  h5: (p) => <h6 className="text-body font-bold text-fg-strong" {...omitNode(p)} />,
  h6: (p) => <h6 className="text-body font-bold text-fg-strong" {...omitNode(p)} />,
  p: (p) => <p className="whitespace-pre-wrap" {...omitNode(p)} />,
  ul: (p) => <ul className="list-disc space-y-1 pl-5" {...omitNode(p)} />,
  ol: (p) => <ol className="list-decimal space-y-1 pl-5" {...omitNode(p)} />,
  a: (p) => (
    <a
      className="break-all text-primary-fg underline"
      target="_blank"
      rel="noreferrer"
      {...omitNode(p)}
    />
  ),
  strong: (p) => <strong className="font-bold text-fg-strong" {...omitNode(p)} />,
  blockquote: (p) => (
    <blockquote className="border-l-2 border-border pl-3 text-fg-muted" {...omitNode(p)} />
  ),
  hr: (p) => <hr className="border-border" {...omitNode(p)} />,
  code: (p) => (
    <code
      className="rounded-sm bg-surface-raised px-1 py-0.5 font-mono text-[0.9em]"
      {...omitNode(p)}
    />
  ),
  pre: (p) => (
    <pre
      className="overflow-x-auto rounded-lg border border-border bg-surface p-3 font-mono text-caption leading-relaxed [&_code]:bg-transparent [&_code]:p-0"
      {...omitNode(p)}
    />
  ),
  table: (p) => (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-caption" {...omitNode(p)} />
    </div>
  ),
  th: (p) => {
    // text-left 를 항상 박으면 author CSS 가 GFM 의 align 속성(`:---:`)을 이겨버린다 —
    // 정렬 지정이 없는 컬럼에만 좌측 정렬 기본값을 준다.
    const { align, ...rest } = omitNode(p) as ComponentProps<'th'>
    return (
      <th
        align={align}
        className={[
          'border border-border bg-surface px-2 py-1 font-medium',
          align ? '' : 'text-left',
        ].join(' ')}
        {...rest}
      />
    )
  },
  td: (p) => <td className="border border-border px-2 py-1" {...omitNode(p)} />,
}

export default function MarkdownContent({ children, className }: MarkdownProps) {
  return (
    <div className={['space-y-2 text-body leading-relaxed', className ?? ''].join(' ')}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
        components={components}
      >
        {children}
      </ReactMarkdown>
    </div>
  )
}
