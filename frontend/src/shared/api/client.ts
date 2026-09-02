import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { env } from '@/shared/config/env'
import { ApiError, type ApiErrorBody } from './errors'
import { tokenStore } from './token-store'

type RetriableConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
  _authToken?: string | null
}

const REFRESH_PATH = '/api/auth/refresh'
// 짧은 cooldown. 폭주 방지 목적이라 길게 잡지 않는다.
const REFRESH_COOLDOWN_MS = 3000

const baseConfig = {
  baseURL: env.API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
} as const

export const apiClient = axios.create(baseConfig)

const refreshClient = axios.create(baseConfig)

apiClient.interceptors.request.use((config) => {
  const cfg = config as RetriableConfig
  const token = tokenStore.get()
  cfg._authToken = token
  if (token) {
    if (!cfg.headers) cfg.headers = new AxiosHeaders()
    cfg.headers.set('Authorization', `Bearer ${token}`)
  }
  return cfg
})

let refreshing: Promise<string> | null = null
let refreshCooldownUntil = 0
let onUnauthorized: (() => void) | null = null
let onTokenRefreshed: ((accessToken: string) => void) | null = null

export function setAuthSideEffects(handlers: {
  onUnauthorized: () => void
  onTokenRefreshed: (accessToken: string) => void
}) {
  onUnauthorized = handlers.onUnauthorized
  onTokenRefreshed = handlers.onTokenRefreshed
}

function isRefreshPayload(data: unknown): data is { accessToken: string } {
  if (typeof data !== 'object' || data === null) return false
  const token = (data as { accessToken?: unknown }).accessToken
  return typeof token === 'string' && token.length > 0
}

async function performRefresh(): Promise<string> {
  const response = await refreshClient.post(REFRESH_PATH, {})
  if (!isRefreshPayload(response.data)) {
    throw new ApiError(response.status, {
      code: 'AUTH_REFRESH_MALFORMED',
      message: 'Refresh response did not contain a valid accessToken',
    })
  }
  const next = response.data.accessToken
  tokenStore.set(next)
  onTokenRefreshed?.(next)
  return next
}

// 401과 403을 분리할 필요가 있다고 생각햤습니다.
function isAuthRefreshFailure(err: unknown): boolean {
  if (err instanceof ApiError) return err.code === 'AUTH_REFRESH_MALFORMED'
  if (err instanceof AxiosError) return err.response?.status === 401
  return false
}

function isTransientRefreshFailure(err: unknown): boolean {
  if (!(err instanceof AxiosError)) return false
  const status = err.response?.status
  // status undefined → network error / CORS / timeout
  return status === undefined || status >= 500 || status === 429
}

function makeTransientAuthError(): ApiError {
  return new ApiError(503, {
    code: 'SYS_DEPENDENCY_DOWN',
    message: '인증 서비스에 일시적으로 연결할 수 없습니다.',
  })
}

function refreshOnce(): Promise<string> {
  if (Date.now() < refreshCooldownUntil) {
    return Promise.reject(makeTransientAuthError())
  }
  if (!refreshing) {
    refreshing = performRefresh()
      .catch((err: unknown) => {
        if (isAuthRefreshFailure(err)) {
          tokenStore.clear()
          onUnauthorized?.()
          throw err
        }
        if (isTransientRefreshFailure(err)) {
          refreshCooldownUntil = Date.now() + REFRESH_COOLDOWN_MS
          throw makeTransientAuthError()
        }
        throw err
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

// 앱 부트스트랩용: 토큰이 없으면 refresh 로 먼저 확보한다.
// 이렇게 해야 첫 인증 요청(/api/users/me)이 토큰 없이 나가 401 을 유발하고
// 재시도되는 낭비가 사라진다. 세션이 없으면(refresh 401) null 을 돌려준다.
export async function ensureAccessToken(): Promise<string | null> {
  const existing = tokenStore.get()
  if (existing) return existing
  try {
    return await refreshOnce()
  } catch (err) {
    // 일시적 장애(SYS_DEPENDENCY_DOWN)는 그대로 던져 상위에서 구분 처리.
    if (err instanceof ApiError && err.code === 'SYS_DEPENDENCY_DOWN') throw err
    // 그 외(세션 없음 등)는 비로그인으로 취급.
    return null
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as RetriableConfig | undefined
    const status = error.response?.status

    if (status !== 401 || !original) {
      return Promise.reject(toApiError(error))
    }

    //로그인 후 자동 재시도 방지
    const currentToken = tokenStore.get()
    if (
      !original._retry &&
      original._authToken !== null &&
      currentToken !== null &&
      currentToken !== original._authToken
    ) {
      original._retry = true
      return apiClient(original)
    }

    if (original._retry) {
      tokenStore.clear()
      onUnauthorized?.()
      return Promise.reject(toApiError(error))
    }

    original._retry = true
    try {
      await refreshOnce()
      return apiClient(original)
    } catch (refreshError) {
      if (
        refreshError instanceof ApiError &&
        refreshError.code === 'SYS_DEPENDENCY_DOWN'
      ) {
        return Promise.reject(refreshError)
      }
      return Promise.reject(toApiError(error))
    }
  },
)

function toApiError(error: unknown): ApiError | Error {
  if (error instanceof ApiError) return error
  if (!(error instanceof AxiosError)) {
    return error instanceof Error ? error : new Error(String(error))
  }
  const status = error.response?.status ?? 0
  const body = error.response?.data
  if (
    body !== null &&
    typeof body === 'object' &&
    typeof (body as ApiErrorBody).code === 'string' &&
    typeof (body as ApiErrorBody).message === 'string'
  ) {
    return new ApiError(status, body as ApiErrorBody)
  }
  return new ApiError(status, {
    code: status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR',
    message: error.message,
  })
}

export type ApiResponse<T> = AxiosResponse<T>
