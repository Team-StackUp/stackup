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

// 탭 사이 직렬화.
//
// refresh 는 서버에서 **회전**한다 — 기존 토큰을 즉시 revoke 하고 새 토큰을 발급한다
// (RefreshTokenService.rotate, 유예 없음). 아래 `refreshing` 단일 비행은 모듈 변수라
// **한 탭 안에서만** 중복을 막는다. 리프레시 쿠키는 탭이 공유하므로, 탭 두 개가 동시에
// 부팅하면(창 복원·새 탭으로 열기) 둘 다 같은 토큰으로 refresh 를 쏘고 진 쪽이
// AUTH_REVOKED_TOKEN(401)을 받아 **그 탭만 로그아웃된다.** 진행 중인 면접 탭이 지면
// 사용자는 이유 없이 튕긴 것으로 본다.
//
// Web Locks 로 origin 전체에서 한 번에 하나만 돌게 한다. 기다린 탭은 A 가 갱신해 둔
// 쿠키로 다시 회전하므로(T2→T3) 정상 동작한다 — 회전이 한 번 더 일어날 뿐이다.
// 락을 못 쓰는 환경(구형 Safari·비보안 컨텍스트)에서는 지금과 같이 그냥 진행한다.
const REFRESH_LOCK = 'stackup-auth-refresh'

export function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks
  if (!locks?.request) return fn()
  // lib.dom 은 콜백의 반환값을 그대로 결과 타입으로 잡는다. 프라미스를 돌려주면
  // Promise<Promise<T>> 로 추론되는데, 플랫폼은 이를 평탄화해 T 로 resolve 한다.
  return locks.request(REFRESH_LOCK, fn) as Promise<T>
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
    refreshing = withRefreshLock(performRefresh)
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
