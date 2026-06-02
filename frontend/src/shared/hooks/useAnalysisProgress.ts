import { useSyncExternalStore } from 'react'

// 분석 단계 진행 상황(휘발성). AI 서버가 RealTime 으로 직접 보내는 ANALYSIS_PROGRESS SSE 를
// 받아 여기에 저장하고, 레포·이력서 목록이 현재 단계 문구를 표시한다.
// 서버 쿼리 캐시와 무관(쿼리 무효화 없이 갱신)하며, 분석 완료/실패 시 clear 된다.
export type AnalysisProgress = {
  phase: string
  message: string
}

const keyOf = (targetType: string, targetId: number) => `${targetType}:${targetId}`

const store = new Map<string, AnalysisProgress>()
const listeners = new Set<() => void>()

function emitChange(): void {
  for (const listener of listeners) listener()
}

export const analysisProgress = {
  set(targetType: string, targetId: number, value: AnalysisProgress): void {
    store.set(keyOf(targetType, targetId), value)
    emitChange()
  },
  clear(targetType: string, targetId: number): void {
    if (store.delete(keyOf(targetType, targetId))) emitChange()
  },
  get(targetType: string, targetId: number): AnalysisProgress | undefined {
    return store.get(keyOf(targetType, targetId))
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

// 동일 (targetType,targetId)의 현재 진행 단계. 없으면 undefined.
export function useAnalysisProgress(
  targetType: string,
  targetId: number,
): AnalysisProgress | undefined {
  return useSyncExternalStore(
    analysisProgress.subscribe,
    () => analysisProgress.get(targetType, targetId),
  )
}
