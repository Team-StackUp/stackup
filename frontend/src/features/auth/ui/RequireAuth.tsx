import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../model/useAuth'

type RequireAuthProps = {
  children: ReactNode
  fallback?: ReactNode
}

export function RequireAuth({ children, fallback }: RequireAuthProps) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading' || status === 'idle') {
    return fallback ?? null
  }

  if (status === 'unauthenticated') {
    const returnTo = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ returnTo }} />
  }

  return <>{children}</>
}
