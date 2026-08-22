import { useSyncExternalStore } from 'react'

// 분석 단계 진행 상황(휘발성). AI 서버가 RealTime 으로 직접 보내는 ANALYSIS_PROGRESS SSE 를
// 받아 여기에 저장하고, 레포·이력서 목록이 현재 단계 문구를 표시한다.
// 서버 쿼리 캐시와 무관(쿼리 무효화 없이 갱신)하며, 분석 완료/실패 시 clear 된다.
export type AnalysisProgress = {
  phase: string
  message: string
}

const keyOf = (targetType: string, targetId: number) => `${targetType}:${targetId}`

// 진행 이벤트는 분석 중 수 초 간격으로 온다. 이 시간 동안 갱신이 없으면 종료 이벤트
// (DOC_STATE/REPO_STATE)가 유실된 것으로 보고 문구를 걷어낸다 — TTL 이 없으면
// "임베딩하는 중…" 이 영구 고착되고, 재분석 시 이전 세션 문구가 되살아난다.
const PROGRESS_TTL_MS = 90_000
const SWEEP_INTERVAL_MS = 15_000

type Entry = AnalysisProgress & { updatedAt: number }

const store = new Map<string, Entry>()
const listeners = new Set<() => void>()
let sweepTimer: ReturnType<typeof setInterval> | null = null

function emitChange(): void {
  for (const listener of listeners) listener()
}

// 스토어가 비어 있지 않은 동안에만 만료 스위퍼를 돌린다.
function sweep(): void {
  const cutoff = Date.now() - PROGRESS_TTL_MS
  let changed = false
  for (const [key, entry] of store) {
    if (entry.updatedAt < cutoff) {
      store.delete(key)
      changed = true
    }
  }
  if (store.size === 0 && sweepTimer !== null) {
    clearInterval(sweepTimer)
    sweepTimer = null
  }
  if (changed) emitChange()
}

export const analysisProgress = {
  set(targetType: string, targetId: number, value: AnalysisProgress): void {
    store.set(keyOf(targetType, targetId), { ...value, updatedAt: Date.now() })
    if (sweepTimer === null) sweepTimer = setInterval(sweep, SWEEP_INTERVAL_MS)
    emitChange()
  },
  clear(targetType: string, targetId: number): void {
    if (store.delete(keyOf(targetType, targetId))) {
      if (store.size === 0 && sweepTimer !== null) {
        clearInterval(sweepTimer)
        sweepTimer = null
      }
      emitChange()
    }
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
