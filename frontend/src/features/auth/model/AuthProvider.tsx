import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { isApiError, setAuthSideEffects, tokenStore } from '@/shared/api'
import { fetchCurrentUser, logout as logoutApi } from '../api/auth'
import {
  AuthContext,
  type AuthContextValue,
  type AuthStatus,
} from './AuthContext'
import type { AuthUser, LoginResponse } from './types'

type AuthProviderProps = {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<AuthUser | null>(null)
  const bootstrappedRef = useRef(false)

  const applyLogin = useCallback((response: LoginResponse) => {
    tokenStore.set(response.accessToken)
    setUser(response.user)
    setStatus('authenticated')
  }, [])

  const clearAuth = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const refreshUser = useCallback(async () => {
    try {
      const me = await fetchCurrentUser()
      setUser(me)
      setStatus('authenticated')
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        clearAuth()
        return
      }
      throw error
    }
  }, [clearAuth])

  const logout = useCallback(async () => {
    try {
      await logoutApi()
    } finally {
      clearAuth()
    }
  }, [clearAuth])

  useEffect(() => {
    setAuthSideEffects({
      onUnauthorized: () => {
        tokenStore.clear()
        setUser(null)
        setStatus('unauthenticated')
      },
      onTokenRefreshed: (token) => {
        tokenStore.set(token)
      },
    })
  }, [])

  useEffect(() => {
    if (bootstrappedRef.current) return
    bootstrappedRef.current = true

    void (async () => {
      try {
        await refreshUser()
      } catch {
        clearAuth()
      }
    })()
  }, [refreshUser, clearAuth])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, applyLogin, logout, refreshUser }),
    [status, user, applyLogin, logout, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
