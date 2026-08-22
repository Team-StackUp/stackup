import { useSyncExternalStore } from 'react'

// 워크스페이스 분석 SSE(/realtime/stream/me)의 건강 상태.
// 'down' 이면 SSE 푸시가 유실될 수 있으므로 목록 쿼리들이 폴백 폴링을 켠다
// (api-conventions §9: SSE 우선, 끊기면 폴링 fallback).
// 스트림을 소유한 훅(useWorkspaceAnalysisStream)만 set 하고, 언마운트 시 'idle' 로
// 되돌린다 — 워크스페이스 밖에서는 폴링이 돌지 않아야 한다.
export type WorkspaceStreamHealth = 'idle' | 'up' | 'down'

let health: WorkspaceStreamHealth = 'idle'
const listeners = new Set<() => void>()

export const workspaceStreamHealth = {
  set(next: WorkspaceStreamHealth): void {
    if (health === next) return
    health = next
    for (const listener of listeners) listener()
  },
  get(): WorkspaceStreamHealth {
    return health
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
}

export function useWorkspaceStreamHealth(): WorkspaceStreamHealth {
  return useSyncExternalStore(workspaceStreamHealth.subscribe, workspaceStreamHealth.get)
}

// 목록 쿼리용 폴백 폴링 간격 — 스트림이 죽었을 때만 5s (api-conventions §9).
export function useAnalysisFallbackPolling(): number | false {
  const health = useWorkspaceStreamHealth()
  return health === 'down' ? 5_000 : false
}
