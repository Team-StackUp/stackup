// 목록 로딩 자리표시 — 고정 높이 카드 n개로 레이아웃 시프트(CLS)를 줄인다.
// 한 줄짜리 '불러오는 중…' 텍스트 대신 실제 목록과 비슷한 골격을 먼저 깔아 둔다.
export function ListSkeleton({
  count = 3,
  label = '불러오는 중…',
  className = '',
}: {
  count?: number
  label?: string
  className?: string
}) {
  return (
    <div role="status" aria-busy="true" className={className}>
      <span className="sr-only">{label}</span>
      <ul aria-hidden className="flex flex-col gap-2">
        {Array.from({ length: count }).map((_, i) => (
          <li
            key={i}
            className="h-16 animate-pulse rounded-xl border border-border bg-surface"
          />
        ))}
      </ul>
    </div>
  )
}
