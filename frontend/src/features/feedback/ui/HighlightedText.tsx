import { Fragment, useMemo } from 'react'

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// 출력 가능한 ASCII(공백~틸드)만으로 이뤄진 term — 영문 키워드 판별용(제어문자 제외).
const ASCII_ONLY = /^[\x20-\x7E]+$/

// 단일 term → 정규식 조각. 순수 ASCII(영문 키워드 'AI'·'Go' 등)는 단어 중간 매칭을
// 막기 위해 ASCII 경계 lookaround 로 감싼다(예: 'AI' 가 "detail"·"main" 내부를 강조하지 않게).
// 한글/혼용 구절은 경계 개념이 모호하고 긴 구절이라 오매칭이 드물어 그대로 둔다.
// lookaround 는 zero-width 라 split 의 '캡처 그룹 1개' 가정을 깨지 않는다.
function termToPattern(t: string): string {
  const esc = escapeRegExp(t)
  return ASCII_ONLY.test(t) ? `(?<![A-Za-z0-9])${esc}(?![A-Za-z0-9])` : esc
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
    return new RegExp(`(${cleaned.map(termToPattern).join('|')})`, 'gi')
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
