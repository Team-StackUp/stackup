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
  const form = new FormData()
  const ext = audio.type.includes('ogg') ? 'ogg' : 'webm'
  form.append('audio', audio, `answer.${ext}`)
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
