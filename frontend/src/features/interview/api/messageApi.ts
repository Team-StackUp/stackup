import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']

export async function listMessages(sessionId: number): Promise<S['MessageResponse'][]> {
  return (await apiClient.get<S['MessageResponse'][]>(`/api/sessions/${sessionId}/messages`)).data
}
