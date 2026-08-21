import { useCallback, useState } from 'react'
import { isApiError } from '@/shared/api'
import { toast } from '@/shared/ui'
import { deleteAccount } from '../api/auth'
import { useAuth } from './useAuth'

/**
 * 회원 탈퇴 (US-04).
 *
 * <p>성공하면 곧바로 로컬 인증 상태를 비운다. `logout()` 은 서버 호출이 실패해도
 * finally 에서 상태를 정리하므로, 이미 지워진 계정이라 로그아웃 API 가 401 을 줘도
 * 화면은 정상적으로 로그아웃된 상태가 된다.
 */
export function useDeleteAccount() {
  const { logout } = useAuth()
  const [deleting, setDeleting] = useState(false)

  const remove = useCallback(async () => {
    if (deleting) return false
    setDeleting(true)
    try {
      await deleteAccount()
    } catch (error) {
      setDeleting(false)
      // 410 = 이미 탈퇴한 계정. 사용자가 원한 결과는 이미 이뤄졌으니 실패로 알리지 않고
      // 로그아웃까지 마무리한다(다른 탭에서 먼저 탈퇴한 경우).
      if (!(isApiError(error) && error.status === 410)) {
        toast.error('탈퇴 처리에 실패했어요. 잠시 후 다시 시도해 주세요.')
        return false
      }
    }
    try {
      await logout()
    } catch {
      // 이미 계정이 사라져 로그아웃 API 가 실패할 수 있다 — 로컬 상태는 이미 정리됐다.
    }
    return true
  }, [deleting, logout])

  return { remove, deleting }
}
