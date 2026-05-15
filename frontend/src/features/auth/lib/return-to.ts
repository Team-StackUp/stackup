const KEY = 'stackup.auth.returnTo'

function isSafePath(value: string | null | undefined): value is string {
  if (!value) return false
  return value.startsWith('/') && !value.startsWith('//')
}

export function rememberReturnTo(path: string | null | undefined): void {
  if (typeof window === 'undefined') return
  if (!isSafePath(path)) {
    window.sessionStorage.removeItem(KEY)
    return
  }
  window.sessionStorage.setItem(KEY, path)
}

export function consumeReturnTo(): string | null {
  if (typeof window === 'undefined') return null
  const value = window.sessionStorage.getItem(KEY)
  window.sessionStorage.removeItem(KEY)
  return isSafePath(value) ? value : null
}
