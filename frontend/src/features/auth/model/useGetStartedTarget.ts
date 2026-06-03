import { useAuth } from './useAuth'

// "Get Started" CTA 목적지. 헤더(SiteNav)와 동일한 분기:
// 로그인 상태면 워크스페이스, 아니면 로그인 페이지.
// (loading 중에도 로그인으로 — 로그인 페이지가 인증 사용자를 워크스페이스로 보낸다.)
export function useGetStartedTarget(): string {
  const { status } = useAuth()
  return status === 'authenticated' ? '/workspace' : '/login'
}
