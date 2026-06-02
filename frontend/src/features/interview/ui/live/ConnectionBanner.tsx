import type { ConnectionStatus } from '../../model/useLiveInterview'

export function ConnectionBanner({ connection }: { connection: ConnectionStatus }) {
  if (connection === 'open') return null
  return (
    <div role="alert" className="bg-warning-50 px-4 py-2 text-center text-caption text-warning-700">
      {connection === 'connecting' ? '면접 서버에 연결 중입니다…' : '연결이 끊겨 재연결 중입니다…'}
    </div>
  )
}
