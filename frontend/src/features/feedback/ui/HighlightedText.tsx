import { Fragment, useMemo } from 'react'

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 피드백 본문에서 주어진 구절/키워드를 <mark> 로 강조한다.
// terms = AI 가 고른 핵심 구절(highlights) ∪ 다음에 채울 키워드(improvementKeywords).
// 긴 구절을 먼저 매칭(부분 겹침 방지), 대소문자 무시. mark 는 html2canvas(PDF)에서도 렌더된다.
export function HighlightedText({
  text,
  terms,
}: {
  text: string
  terms: (string | null | undefined)[]
}) {
  const regex = useMemo(() => {
    const cleaned = Array.from(
      new Set(terms.map((t) => (t ?? '').trim()).filter((t) => t.length >= 2)),
    ).sort((a, b) => b.length - a.length)
    if (cleaned.length === 0) return null
    return new RegExp(`(${cleaned.map(escapeRegExp).join('|')})`, 'gi')
  }, [terms])

  if (!regex || !text) return <>{text}</>

  // 캡처 그룹 1개로 split → 홀수 인덱스가 매칭부(원문 대소문자 보존).
  const parts = text.split(regex)
  return (
    <>
      {parts.map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded-sm bg-warning-50 px-0.5 text-fg-strong">
            {part}
          </mark>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  )
}
