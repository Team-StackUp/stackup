import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useQuestionRunner } from './useQuestionRunner'

const KEY = 'test:answers'

beforeEach(() => window.localStorage.clear())

describe('useQuestionRunner', () => {
  it('질문을 순서대로 넘기고 마지막을 알려준다', () => {
    const { result } = renderHook(() => useQuestionRunner(['a', 'b']))

    expect(result.current.currentId).toBe('a')
    expect(result.current.isLast).toBe(false)

    act(() => result.current.next())
    expect(result.current.currentId).toBe('b')
    expect(result.current.isLast).toBe(true)
    expect(result.current.done).toBe(false)

    act(() => result.current.next())
    expect(result.current.done).toBe(true)
  })

  // 정답을 본 상태가 다음 질문으로 새면 곧바로 답이 보인다.
  it('다음 질문으로 넘어가면 정답 공개가 닫힌다', () => {
    const { result } = renderHook(() => useQuestionRunner(['a', 'b']))

    act(() => result.current.reveal())
    expect(result.current.revealed).toBe(true)

    act(() => result.current.next())
    expect(result.current.revealed).toBe(false)
  })

  // A-7: 새로고침하면 사용자가 적은 메모가 통째로 사라지던 문제.
  it('답변 메모를 저장하고 다시 불러온다', () => {
    const first = renderHook(() => useQuestionRunner(['a'], KEY))
    act(() => first.result.current.setAnswer('a', '내가 적은 답'))
    first.unmount()

    const second = renderHook(() => useQuestionRunner(['a'], KEY))
    expect(second.result.current.answers.a).toBe('내가 적은 답')
  })

  it('storageKey 가 없으면 저장하지 않는다', () => {
    const { result } = renderHook(() => useQuestionRunner(['a']))

    act(() => result.current.setAnswer('a', 'x'))

    expect(window.localStorage.length).toBe(0)
  })

  it('처음부터 다시 하면 저장된 답변도 지운다', () => {
    const { result } = renderHook(() => useQuestionRunner(['a', 'b'], KEY))
    act(() => result.current.setAnswer('a', 'x'))
    act(() => result.current.next())

    act(() => result.current.reset())

    expect(result.current.index).toBe(0)
    expect(result.current.answers).toEqual({})
    // 저장은 이펙트가 단독으로 책임진다 — 비운 상태가 그대로 반영된다.
    expect(window.localStorage.getItem(KEY)).toBe('{}')
  })

  // 다른 코드가 같은 키를 썼거나 형식이 바뀐 경우 — 드릴이 죽으면 안 된다.
  it('저장된 값이 망가져 있으면 빈 상태로 시작한다', () => {
    window.localStorage.setItem(KEY, 'not json')
    expect(renderHook(() => useQuestionRunner(['a'], KEY)).result.current.answers).toEqual({})

    window.localStorage.setItem(KEY, '[1,2,3]')
    expect(renderHook(() => useQuestionRunner(['a'], KEY)).result.current.answers).toEqual({})

    window.localStorage.setItem(KEY, '{"a":"ok","b":42}')
    expect(renderHook(() => useQuestionRunner(['a'], KEY)).result.current.answers).toEqual({
      a: 'ok',
    })
  })

  // 오답노트에서 항목을 빼면 목록이 짧아진다 — index 가 그대로면 빈 화면이 된다.
  it('질문 목록이 짧아지면 범위 안으로 접힌다', () => {
    const { result, rerender } = renderHook(({ ids }) => useQuestionRunner(ids), {
      initialProps: { ids: ['a', 'b', 'c'] },
    })

    act(() => result.current.next())
    act(() => result.current.next())
    expect(result.current.currentId).toBe('c')

    rerender({ ids: ['a'] })

    expect(result.current.index).toBe(1)
    expect(result.current.done).toBe(true)
  })
})
