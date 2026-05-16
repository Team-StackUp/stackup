import { useCallback, useState } from 'react'
import { useAuth } from './useAuth'

export function useLogout() {
  const { logout: logoutFromAuth } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  const logout = useCallback(async () => {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await logoutFromAuth()
    } finally {
      setLoggingOut(false)
    }
  }, [loggingOut, logoutFromAuth])

  return { logout, loggingOut }
}
