import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']

export async function createSession(body: S['SessionCreateRequest']): Promise<S['SessionResponse']> {
  return (await apiClient.post<S['SessionResponse']>('/api/sessions', body)).data
}

export async function getSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.get<S['SessionResponse']>(`/api/sessions/${id}`)).data
}

// 같은 설정으로 새 세션 생성. 삭제된 자료는 서버가 알아서 빼고 잇는다(응답의 contextDocumentIds).
export async function retrySession(
  id: number,
  options: { focusOnWeakness?: boolean } = {},
): Promise<S['SessionResponse']> {
  return (
    await apiClient.post<S['SessionResponse']>(`/api/sessions/${id}/retry`, {
      focusOnWeakness: options.focusOnWeakness ?? false,
    })
  ).data
}

export async function startSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/start`)).data
}

// 중단된 면접 이어하기. 서버가 상태 전이 + 끊긴 턴 복구까지 한다.
export async function resumeSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/resume`)).data
}

export async function endSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/end`)).data
}

export async function interruptSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/interrupt`)).data
}
