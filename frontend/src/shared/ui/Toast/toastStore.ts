// 도메인 비종속 토스트 스토어. 모듈 싱글톤 + 구독 모델이라 훅 밖(예: 뮤테이션 onSuccess)에서도
// toast.success(...) 로 호출할 수 있다. ToastViewport 가 useSyncExternalStore 로 구독한다.

export type ToastTone = 'success' | 'error' | 'info'
export type ToastItem = { id: number; tone: ToastTone; message: string }

const DEFAULT_DURATION_MS = 4000

let items: ToastItem[] = []
let seq = 0
const listeners = new Set<() => void>()
const timers = new Map<number, ReturnType<typeof setTimeout>>()

function emit() {
  for (const l of listeners) l()
}

export function subscribeToasts(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

// useSyncExternalStore 는 변경이 없으면 동일 참조를 기대하므로 items 를 그대로 반환한다.
export function getToasts(): ToastItem[] {
  return items
}

export function dismissToast(id: number): void {
  const t = timers.get(id)
  if (t) {
    clearTimeout(t)
    timers.delete(id)
  }
  const next = items.filter((i) => i.id !== id)
  if (next.length !== items.length) {
    items = next
    emit()
  }
}

function push(tone: ToastTone, message: string, durationMs = DEFAULT_DURATION_MS): number {
  const id = ++seq
  items = [...items, { id, tone, message }]
  emit()
  if (durationMs > 0) {
    timers.set(
      id,
      setTimeout(() => dismissToast(id), durationMs),
    )
  }
  return id
}

export const toast = {
  success: (message: string, durationMs?: number) => push('success', message, durationMs),
  error: (message: string, durationMs?: number) => push('error', message, durationMs),
  info: (message: string, durationMs?: number) => push('info', message, durationMs),
}
