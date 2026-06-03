import { useCallback, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { selectQuestions } from '@/domain/practice'
import type { PracticeTrack } from '@/domain/practice'
import { loadQuestionBank } from '../api/loadQuestionBank'

const DEFAULT_COUNT = 8

export function usePracticeSession(track: PracticeTrack, count: number = DEFAULT_COUNT) {
  const bankQuery = useQuery({
    queryKey: ['practice-bank', track],
    queryFn: () => loadQuestionBank(track),
    staleTime: Infinity,
  })

  // seed 가 바뀌면 같은 은행에서 질문을 다시 뽑는다(다시 풀기).
  const [seed, setSeed] = useState(0)
  const [index, setIndex] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [answers, setAnswers] = useState<Record<string, string>>({})

  const questions = useMemo(() => {
    if (!bankQuery.data) return []
    return selectQuestions(bankQuery.data, count)
    // seed 를 의존성에 포함해 "다시 풀기" 시 새 표본을 뽑는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bankQuery.data, count, seed])

  const total = questions.length
  const current = questions[index]
  const isLast = index >= total - 1
  const done = total > 0 && index >= total

  const reveal = useCallback(() => setRevealed(true), [])

  const next = useCallback(() => {
    setRevealed(false)
    setIndex((i) => i + 1)
  }, [])

  const setAnswer = useCallback((id: string, value: string) => {
    setAnswers((prev) => ({ ...prev, [id]: value }))
  }, [])

  const restart = useCallback(() => {
    setIndex(0)
    setRevealed(false)
    setAnswers({})
    setSeed((s) => s + 1)
  }, [])

  return {
    bankTitle: bankQuery.data?.title,
    isLoading: bankQuery.isLoading,
    isError: bankQuery.isError,
    error: bankQuery.error as Error | undefined,
    refetch: bankQuery.refetch,
    questions,
    current,
    index,
    total,
    isLast,
    done,
    revealed,
    answers,
    reveal,
    next,
    setAnswer,
    restart,
  }
}
