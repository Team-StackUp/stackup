import { type ReactNode } from 'react'
import type { SectionId } from './nav'
import { readableFg } from './lib'

/**
 * 데모 페이지 전용 UI 프리미티브.
 *  - Section / Sub : 섹션 / 하위 섹션 헤딩
 *  - Swatch        : 컬러 스와치 카드
 *  - Code          : 인라인 코드 / 토큰 배지
 *  - TableHead     : 공통 테이블 헤더
 *
 * 모두 디자인 시스템 토큰만 사용 — 하드코딩된 색상 없음.
 */

export function Section({
  id,
  label,
  title,
  description,
  children,
}: {
  id: SectionId
  label: string
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <section id={id} className="mb-24 scroll-mt-10">
      <header className="mb-10 pb-6 border-b border-border">
        <div className="text-caption text-fg-muted uppercase tracking-[0.18em] mb-3 font-semibold font-mono">
          {label}
        </div>
        <h2 className="font-heading text-h3 uppercase tracking-tight text-fg-strong leading-none mb-3">
          {title}
        </h2>
        {description && (
          <p className="text-rich text-fg-muted max-w-2xl">{description}</p>
        )}
      </header>
      {children}
    </section>
  )
}

export function Sub({
  title,
  action,
  children,
}: {
  title: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="mb-12 last:mb-0">
      <div className="flex items-baseline justify-between mb-5 gap-3">
        <h3 className="font-subheading text-h6 font-bold text-fg-strong">
          {title}
        </h3>
        {action}
      </div>
      {children}
    </div>
  )
}

export function Swatch({
  token,
  hex,
  sub,
}: {
  token: string
  hex: string
  sub?: string
}) {
  const fg = readableFg(hex)
  return (
    <div className="rounded-md overflow-hidden border border-border bg-surface-raised hover:border-border-strong transition-colors duration-fast">
      <div
        className="h-16 flex flex-col justify-end p-2 font-mono text-[10px] leading-none"
        style={{ backgroundColor: hex, color: fg }}
      >
        {hex.toUpperCase()}
      </div>
      <div className="px-2.5 py-2">
        <div className="text-caption font-semibold text-fg truncate">{token}</div>
        {sub && (
          <div className="text-[10px] text-fg-muted mt-0.5 truncate">{sub}</div>
        )}
      </div>
    </div>
  )
}

export function Code({ children }: { children: ReactNode }) {
  return (
    <code className="inline-block px-1.5 py-0.5 rounded-sm bg-sage-50 border border-sage-200 text-caption font-mono text-sage-700 leading-tight">
      {children}
    </code>
  )
}

export function TableHead({ cols }: { cols: string[] }) {
  return (
    <thead className="bg-surface text-fg-muted text-caption uppercase tracking-wider">
      <tr>
        {cols.map((c) => (
          <th key={c} className="px-4 py-3 text-left font-semibold">
            {c}
          </th>
        ))}
      </tr>
    </thead>
  )
}
