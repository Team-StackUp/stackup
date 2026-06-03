import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchMessageAudioObjectUrl } from '../../api/messageApi'

// 메시지 오디오를 Core 프록시로 1회 받아 object URL 로 캐싱(언마운트 시 revoke).
// load() 는 처음 호출 시 fetch, 이후 캐시 반환. id 가 없으면 비활성.
export function useMessageAudio(sessionId?: number, messageId?: number) {
  const [url, setUrl] = useState<string>()
  const loadingRef = useRef(false)

  const load = useCallback(async (): Promise<string | undefined> => {
    if (url) return url
    if (loadingRef.current || sessionId == null || messageId == null) return undefined
    loadingRef.current = true
    try {
      const u = await fetchMessageAudioObjectUrl(sessionId, messageId)
      setUrl(u)
      return u
    } catch {
      return undefined
    } finally {
      loadingRef.current = false
    }
  }, [url, sessionId, messageId])

  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url)
    },
    [url],
  )

  return { url, load }
}
