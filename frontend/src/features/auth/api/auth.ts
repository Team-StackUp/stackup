import { apiClient } from '@/shared/api'
import type {
  AuthUser,
  GithubAuthorizeResponse,
  LoginResponse,
} from '../model/types'

export async function startGithubLogin(): Promise<GithubAuthorizeResponse> {
  const response = await apiClient.post<GithubAuthorizeResponse>(
    '/api/auth/github',
    {},
  )
  return response.data
}

export async function completeGithubLogin(
  code: string,
  state: string,
): Promise<LoginResponse> {
  const response = await apiClient.get<LoginResponse>(
    '/api/auth/github/callback',
    {
      params: { code, state },
    },
  )
  return response.data
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  const response = await apiClient.get<AuthUser>('/api/users/me')
  return response.data
}

export async function logout(): Promise<void> {
  await apiClient.delete('/api/auth/logout')
}

export async function createStreamToken(): Promise<string> {
  const response = await apiClient.post<{ streamToken: string }>(
    '/api/auth/stream-token',
    {},
  )
  return response.data.streamToken
}
