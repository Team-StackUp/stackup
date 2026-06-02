import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']

export async function createSession(body: S['SessionCreateRequest']): Promise<S['SessionResponse']> {
  return (await apiClient.post<S['SessionResponse']>('/api/sessions', body)).data
}

export async function getSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.get<S['SessionResponse']>(`/api/sessions/${id}`)).data
}

export async function startSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/start`)).data
}

export async function endSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/end`)).data
}

export async function interruptSession(id: number): Promise<S['SessionResponse']> {
  return (await apiClient.patch<S['SessionResponse']>(`/api/sessions/${id}/interrupt`)).data
}
