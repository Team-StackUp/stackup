import type { PracticeQuestion, QuestionBank } from '../model/types'

// 각 질문 JSON 은 과목(subject)·카테고리·질문 순서가 모두 "빈출 → 마이너" 로
// 정렬되어 있다. 따라서 앞쪽 과목/질문일수록 더 자주 출제되도록 가중치를 준다.
//
// - subjectWeight: 앞선 과목일수록 큰 선형 가중치 (N, N-1, … , 1).
// - positionWeight: 카테고리 내 뒤쪽 질문일수록 완만하게 감소.
// - categoryPenalty: 한 카테고리에서 뽑을 때마다 같은 카테고리의 남은 가중치를
//   줄여, 특정 영역에 쏠리지 않고 카테고리가 고르게 분배되도록 한다.
const POSITION_DECAY = 0.15
const CATEGORY_PENALTY = 0.25

interface PoolEntry {
  question: PracticeQuestion
  categoryId: string
  weight: number
}

function buildPool(bank: QuestionBank): PoolEntry[] {
  const pool: PoolEntry[] = []
  const subjectCount = bank.subjects.length

  bank.subjects.forEach((subject, si) => {
    const subjectWeight = subjectCount - si
    subject.categories.forEach((category) => {
      category.questions.forEach((q, qi) => {
        pool.push({
          question: {
            id: q.id,
            question: q.question,
            answer: q.answer,
            subject: subject.name,
            category: category.name,
          },
          categoryId: category.id,
          weight: subjectWeight * (1 / (1 + qi * POSITION_DECAY)),
        })
      })
    })
  })

  return pool
}

function pickWeightedIndex(pool: PoolEntry[], rng: () => number): number {
  const total = pool.reduce((sum, e) => sum + e.weight, 0)
  if (total <= 0) return Math.floor(rng() * pool.length)

  let r = rng() * total
  for (let i = 0; i < pool.length; i++) {
    r -= pool[i].weight
    if (r <= 0) return i
  }
  return pool.length - 1
}

/**
 * 질문 은행에서 가중 무작위로 `count` 개의 질문을 (중복 없이) 선택한다.
 * 앞선 과목/질문에 가중치를 두되 카테고리가 한쪽으로 쏠리지 않게 분배한다.
 *
 * @param rng 0 이상 1 미만 난수 생성기 (테스트에서 주입 가능, 기본 Math.random)
 */
export function selectQuestions(
  bank: QuestionBank,
  count: number,
  rng: () => number = Math.random,
): PracticeQuestion[] {
  const pool = buildPool(bank)
  const target = Math.min(count, pool.length)
  const picked: PracticeQuestion[] = []

  for (let k = 0; k < target; k++) {
    const idx = pickWeightedIndex(pool, rng)
    const [chosen] = pool.splice(idx, 1)
    picked.push(chosen.question)
    for (const entry of pool) {
      if (entry.categoryId === chosen.categoryId) entry.weight *= CATEGORY_PENALTY
    }
  }

  return picked
}
