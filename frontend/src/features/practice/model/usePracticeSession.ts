import { useCallback, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { selectQuestions } from '@/domain/practice'
import type { PracticeTrack } from '@/domain/practice'
import { loadQuestionBank } from '../api/loadQuestionBank'
import { useQuestionRunner } from '@/shared/hooks'

const DEFAULT_COUNT = 8

// 트랙별로 답변 메모를 따로 보관한다. 프론트 로컬 전용이라 서버 계약과 무관.
const storageKeyFor = (track: PracticeTrack) => `stackup:practice-answers:${track}`

export function usePracticeSession(track: PracticeTrack, count: number = DEFAULT_COUNT) {
  const bankQuery = useQuery({
    queryKey: ['practice-bank', track],
    queryFn: () => loadQuestionBank(track),
    staleTime: Infinity,
  })

  // seed 가 바뀌면 같은 은행에서 질문을 다시 뽑는다(다시 풀기).
  const [seed, setSeed] = useState(0)

  const questions = useMemo(() => {
    if (!bankQuery.data) return []
    return selectQuestions(bankQuery.data, count)
    // seed 를 의존성에 포함해 "다시 풀기" 시 새 표본을 뽑는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bankQuery.data, count, seed])

  const questionIds = useMemo(() => questions.map((q) => q.id), [questions])
  const runner = useQuestionRunner(questionIds, storageKeyFor(track))

  const restart = useCallback(() => {
    runner.reset()
    setSeed((s) => s + 1)
  }, [runner])

  return {
    bankTitle: bankQuery.data?.title,
    isLoading: bankQuery.isLoading,
    isError: bankQuery.isError,
    error: bankQuery.error as Error | undefined,
    refetch: bankQuery.refetch,
    questions,
    current: questions[runner.index],
    index: runner.index,
    total: runner.total,
    isLast: runner.isLast,
    done: runner.done,
    revealed: runner.revealed,
    answers: runner.answers,
    reveal: runner.reveal,
    next: runner.next,
    setAnswer: runner.setAnswer,
    restart,
  }
}
