import { useSyncExternalStore } from 'react'
import { createPortal } from 'react-dom'
import {
  dismissToast,
  getToasts,
  subscribeToasts,
  type ToastTone,
} from './toastStore'

const toneClass: Record<ToastTone, string> = {
  success: 'border-l-success text-success-700',
  error: 'border-l-danger text-danger-700',
  info: 'border-l-info text-info-700',
}

function ToneIcon({ tone }: { tone: ToastTone }) {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      {tone === 'success' && <path d="M4 10.5l4 4 8-9" />}
      {tone === 'error' && <path d="M10 6v5M10 14h.01" />}
      {tone === 'info' && <path d="M10 9v5M10 6h.01" />}
    </svg>
  )
}

// 화면 우하단(모바일 상단)에 토스트 스택을 렌더한다. AppProviders 에 1회 마운트.
export function ToastViewport() {
  const items = useSyncExternalStore(subscribeToasts, getToasts, getToasts)
  if (typeof document === 'undefined' || items.length === 0) return null

  return createPortal(
    <div
      role="region"
      aria-label="알림"
      className="pointer-events-none fixed inset-x-0 top-4 flex flex-col items-center gap-2 px-4 sm:inset-x-auto sm:bottom-6 sm:right-6 sm:top-auto sm:items-end"
      style={{ zIndex: 'var(--z-toast)' }}
    >
      {items.map((t) => (
        <div
          key={t.id}
          role="status"
          aria-live="polite"
          className={`pointer-events-auto flex w-full max-w-sm items-start gap-2.5 rounded-lg border border-border border-l-4 bg-surface-raised px-4 py-3 shadow-lg ${toneClass[t.tone]}`}
        >
          <span className="mt-0.5 shrink-0">
            <ToneIcon tone={t.tone} />
          </span>
          <p className="min-w-0 flex-1 text-body text-fg">{t.message}</p>
          <button
            type="button"
            onClick={() => dismissToast(t.id)}
            aria-label="알림 닫기"
            className="-mr-1 shrink-0 rounded p-0.5 text-fg-subtle transition-colors hover:text-fg"
          >
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden>
              <path d="M5 5l10 10M15 5L5 15" />
            </svg>
          </button>
        </div>
      ))}
    </div>,
    document.body,
  )
}
