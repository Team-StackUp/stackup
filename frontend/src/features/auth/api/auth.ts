import { apiClient } from '@/shared/api'
import type {
  AuthUser,
  LoginResponse,
  OAuthAuthorizeResponse,
} from '../model/types'

export async function startGithubLogin(): Promise<OAuthAuthorizeResponse> {
  const response = await apiClient.post<OAuthAuthorizeResponse>(
    '/api/auth/github',
    {},
  )
  return response.data
}

export async function startGoogleLogin(): Promise<OAuthAuthorizeResponse> {
  const response = await apiClient.post<OAuthAuthorizeResponse>(
    '/api/auth/google',
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

export async function completeGoogleLogin(
  code: string,
  state: string,
): Promise<LoginResponse> {
  const response = await apiClient.get<LoginResponse>(
    '/api/auth/google/callback',
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
