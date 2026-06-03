import { describe, it, expect } from 'vitest'
import type { QuestionBank } from '../model/types'
import { selectQuestions } from './selectQuestions'

const q = (id: string) => ({ id, question: `Q-${id}`, answer: `A-${id}` })

function bank(spec: Record<string, Record<string, number>>): QuestionBank {
  return {
    version: '1.0',
    title: 'test',
    description: 'test',
    subjects: Object.entries(spec).map(([sid, cats]) => ({
      id: sid,
      name: sid,
      categories: Object.entries(cats).map(([cid, n]) => ({
        id: cid,
        name: cid,
        questions: Array.from({ length: n }, (_, i) => q(`${cid}-${i}`)),
      })),
    })),
  }
}

// 결정적 RNG: 시퀀스를 순환하며 반환.
function seq(values: number[]): () => number {
  let i = 0
  return () => values[i++ % values.length]
}

describe('selectQuestions', () => {
  it('요청 개수만큼 중복 없이 반환한다', () => {
    const b = bank({ s1: { c1: 5, c2: 5 }, s2: { c3: 5 } })
    const picked = selectQuestions(b, 6, seq([0.1, 0.5, 0.9, 0.3, 0.7]))
    expect(picked).toHaveLength(6)
    expect(new Set(picked.map((p) => p.id)).size).toBe(6)
  })

  it('풀보다 많이 요청하면 풀 크기로 제한된다', () => {
    const b = bank({ s1: { c1: 2 } })
    expect(selectQuestions(b, 10)).toHaveLength(2)
  })

  it('과목/카테고리 메타데이터를 보존한다', () => {
    const b = bank({ Frontend: { html: 1 } })
    const [only] = selectQuestions(b, 1)
    expect(only).toMatchObject({ subject: 'Frontend', category: 'html', id: 'html-0' })
  })

  it('rng=0 이면 가중치가 가장 큰 앞쪽 과목의 첫 질문이 먼저 나온다', () => {
    const b = bank({ s1: { c1: 3 }, s2: { c2: 3 }, s3: { c3: 3 } })
    // 누적 가중치 스캔에서 r<=0 즉시 첫 항목 선택 → 첫 과목 c1 의 첫 질문.
    const [first] = selectQuestions(b, 1, () => 0)
    expect(first.subject).toBe('s1')
  })

  it('빈 은행에서는 빈 배열을 반환한다', () => {
    expect(selectQuestions(bank({}), 5)).toEqual([])
  })
})
