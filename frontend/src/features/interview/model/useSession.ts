import { useQuery } from '@tanstack/react-query'
import { getSession } from '../api/sessionApi'

export const sessionKeys = {
  all: ['sessions'] as const,
  detail: (id: number) => [...sessionKeys.all, 'detail', id] as const,
}

export function useSession(sessionId: number) {
  return useQuery({
    queryKey: sessionKeys.detail(sessionId),
    queryFn: () => getSession(sessionId),
    // 라이브 상태 갱신은 WS 푸시가 1차 경로지만, WS 가 끊긴 사이 스위퍼가 세션을
    // 종료하는 등의 변화는 밀어줄 수단이 없다. 진행 중일 때만 저빈도 폴링을 깔아
    // "이미 끝난 세션 화면에 무한 체류"를 막는다(refetchOnWindowFocus 도 꺼져 있음).
    refetchInterval: (query) =>
      query.state.data?.status === 'IN_PROGRESS' ? 15_000 : false,
  })
}
