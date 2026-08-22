import type { StreamConnectionStatus } from '@/shared/hooks'

// 실시간 채널(WS/SSE)의 단절을 알리는 경고 배너. 'open' 이면 그리지 않는다.
export function ConnectionBanner({
  connection,
  connectingText = '서버에 연결 중입니다…',
  reconnectingText = '연결이 끊겨 재연결 중입니다…',
}: {
  connection: StreamConnectionStatus
  connectingText?: string
  reconnectingText?: string
}) {
  if (connection === 'open') return null
  return (
    <div role="alert" className="bg-warning-50 px-4 py-2 text-center text-caption text-warning-700">
      {connection === 'connecting' ? connectingText : reconnectingText}
    </div>
  )
}
