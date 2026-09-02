import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  ensureAccessToken,
  isApiError,
  setAuthSideEffects,
  tokenStore,
} from '@/shared/api'
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
  const queryClient = useQueryClient()
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
    // 이전 사용자의 이력서·히스토리 캐시가 남으면 공용 PC 에서 다음 로그인 사용자에게
    // 그대로 렌더된다(개인정보 노출). 인증이 사라지는 시점에 서버 상태 캐시도 비운다.
    queryClient.clear()
    setUser(null)
    setStatus('unauthenticated')
  }, [queryClient])

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
        queryClient.clear()
        setUser(null)
        setStatus('unauthenticated')
      },
      onTokenRefreshed: (token) => {
        tokenStore.set(token)
      },
    })
  }, [queryClient])

  useEffect(() => {
    if (bootstrappedRef.current) return
    bootstrappedRef.current = true

    void (async () => {
      try {
        // 토큰을 refresh 로 먼저 확보한 뒤 사용자 정보를 부른다.
        // (토큰 없이 /users/me 를 쏴 401 → refresh → 재시도하던 낭비 제거)
        const token = await ensureAccessToken()
        if (!token) {
          clearAuth()
          return
        }
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
