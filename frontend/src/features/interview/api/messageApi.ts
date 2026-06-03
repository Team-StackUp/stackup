import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']

export async function listMessages(sessionId: number): Promise<S['MessageResponse'][]> {
  return (await apiClient.get<S['MessageResponse'][]>(`/api/sessions/${sessionId}/messages`)).data
}

// 음성 답변 업로드(multipart). 응답은 transcribing placeholder 메시지.
// STT 완료 후 SESSION_MESSAGE SSE 로 transcript 가 도착한다.
export async function submitVoiceAnswer(
  sessionId: number,
  audio: Blob,
  idempotencyKey?: string,
): Promise<S['MessageResponse']> {
  // MediaRecorder 는 "audio/webm;codecs=opus" 처럼 코덱 파라미터를 붙이는데
  // 일부 검증은 base MIME 만 허용하므로 파라미터를 떼고 업로드한다.
  const baseType = (audio.type || 'audio/webm').split(';')[0]
  const clean = audio.type === baseType ? audio : new Blob([audio], { type: baseType })
  const ext = baseType.includes('ogg') ? 'ogg' : 'webm'
  const form = new FormData()
  form.append('audio', clean, `answer.${ext}`)
  const { data } = await apiClient.post<S['MessageResponse']>(
    `/api/sessions/${sessionId}/messages/voice`,
    form,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
    },
  )
  return data
}

// 오디오 바이트를 Core 프록시로 받아 object URL 로 변환.
// MinIO presigned URL 이 내부 호스트라 브라우저가 직접 못 가므로 이 경로를 쓴다.
export async function fetchMessageAudioObjectUrl(
  sessionId: number,
  messageId: number,
): Promise<string> {
  const { data } = await apiClient.get<Blob>(
    `/api/sessions/${sessionId}/messages/${messageId}/audio`,
    { responseType: 'blob' },
  )
  return URL.createObjectURL(data)
}
