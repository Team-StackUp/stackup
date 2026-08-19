import { useCallback, useEffect, useState } from 'react'

/**
 * 질문을 한 개씩 넘기며 답을 적고 정답을 확인하는 드릴의 상태 기계.
 *
 * 연습 면접(정적 질문 은행)과 오답노트(북마크한 면접 질문)가 같은 흐름을 쓰므로
 * 질문 목록을 어디서 얻는지와 분리했다. 두 feature 가 공유하므로 shared 에 둔다 —
 * features 끼리는 서로 import 할 수 없다(FSD).
 *
 * @param questionIds 진행할 질문 id 목록. 순서가 곧 출제 순서다.
 * @param storageKey  주면 답변 메모를 localStorage 에 남긴다. 새로고침·이탈해도 살아남는다.
 */
export function useQuestionRunner(questionIds: string[], storageKey?: string) {
  const [index, setIndex] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [answers, setAnswers] = useState<Record<string, string>>(() =>
    readStored(storageKey),
  )

  // 답변 메모는 사용자가 직접 쓴 것이라 잃으면 손실이 크다(질문 목록은 다시 만들면 그만).
  // 쓰기 실패(용량 초과·프라이빗 모드)는 무시한다 — 드릴 자체가 멈추면 안 된다.
  useEffect(() => {
    if (!storageKey) return
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(answers))
    } catch {
      /* 저장 실패는 조용히 넘긴다 */
    }
  }, [storageKey, answers])

  const total = questionIds.length
  // 질문 목록이 짧아졌는데 index 가 그대로면 빈 화면이 된다(오답노트에서 항목을 빼는 경우).
  // 이펙트로 되돌리면 한 프레임 깜빡이므로 렌더 중에 파생시킨다.
  const safeIndex = Math.min(index, total)
  const currentId = questionIds[safeIndex]
  const isLast = safeIndex >= total - 1
  const done = total > 0 && safeIndex >= total

  const reveal = useCallback(() => setRevealed(true), [])

  const next = useCallback(() => {
    setRevealed(false)
    setIndex((i) => i + 1)
  }, [])

  const setAnswer = useCallback((id: string, value: string) => {
    setAnswers((prev) => ({ ...prev, [id]: value }))
  }, [])

  // 저장은 아래 이펙트 하나가 책임진다. 여기서 removeItem 을 해도 answers 변경으로
  // 이펙트가 곧바로 "{}" 를 다시 써서 무의미하다(읽을 때 빈 맵과 부재는 같다).
  const reset = useCallback(() => {
    setIndex(0)
    setRevealed(false)
    setAnswers({})
  }, [])

  return {
    index: safeIndex,
    currentId,
    total,
    isLast,
    done,
    revealed,
    answers,
    reveal,
    next,
    setAnswer,
    reset,
  }
}

function readStored(storageKey?: string): Record<string, string> {
  if (!storageKey) return {}
  try {
    const raw = window.localStorage.getItem(storageKey)
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    // 남의 키를 덮어쓰거나 형식이 바뀐 경우를 대비해 문자열 맵만 받아들인다.
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.fromEntries(
      Object.entries(parsed as Record<string, unknown>).filter(
        ([, v]) => typeof v === 'string',
      ),
    ) as Record<string, string>
  } catch {
    return {}
  }
}
