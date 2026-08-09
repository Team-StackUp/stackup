/**
 * 컬러 모드(라이트/다크) — SEED 토큰 전환을 담당한다.
 *
 * SEED 는 `<html>` 의 두 속성으로 팔레트를 고른다(`@seed-design/css/base.css`):
 *   data-seed-color-mode        = system | light-only | dark-only
 *   data-seed-user-color-scheme = light | dark      ← mode=system 일 때만 사용
 *
 * 주의: SEED 의 팔레트 블록에는 `prefers-color-scheme` 미디어쿼리가 없다.
 * 즉 mode=system 이어도 `data-seed-user-color-scheme` 를 우리가 직접 써 주지 않으면
 * OS 다크 설정이 반영되지 않는다 — 그래서 matchMedia 를 구독한다.
 */

export const COLOR_MODES = ['system', 'light-only', 'dark-only'] as const
export type ColorMode = (typeof COLOR_MODES)[number]

const STORAGE_KEY = 'stackup.color-mode'
const DARK_QUERY = '(prefers-color-scheme: dark)'

export function isColorMode(value: unknown): value is ColorMode {
  return typeof value === 'string' && (COLOR_MODES as readonly string[]).includes(value)
}

export function readStoredColorMode(): ColorMode {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return isColorMode(raw) ? raw : 'system'
  } catch {
    // 프라이빗 모드 등에서 localStorage 접근이 막힐 수 있다 — 기본값으로 넘어간다.
    return 'system'
  }
}

export function storeColorMode(mode: ColorMode): void {
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {
    // 저장 실패는 무시 — 이번 세션 동안은 정상 동작한다.
  }
}

/** `<html>` 에 SEED 가 읽는 속성을 반영한다. */
export function applyColorMode(mode: ColorMode): void {
  const root = document.documentElement
  root.dataset.seedColorMode = mode
  const prefersDark = window.matchMedia?.(DARK_QUERY).matches ?? false
  root.dataset.seedUserColorScheme =
    mode === 'dark-only' || (mode === 'system' && prefersDark) ? 'dark' : 'light'
}

/** OS 설정 변화를 구독한다. mode=system 일 때만 의미가 있다. */
export function subscribeSystemScheme(onChange: () => void): () => void {
  const mq = window.matchMedia?.(DARK_QUERY)
  if (!mq) return () => {}
  mq.addEventListener('change', onChange)
  return () => mq.removeEventListener('change', onChange)
}

/** 지금 실제로 다크가 적용된 상태인지. 토글 UI 표기에 쓴다. */
export function isDarkApplied(): boolean {
  return document.documentElement.dataset.seedUserColorScheme === 'dark'
}
