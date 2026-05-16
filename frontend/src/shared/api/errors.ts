export const AUTH_ERROR_CODE = {
  INVALID_TOKEN: 'AUTH_INVALID_TOKEN',
  GITHUB_OAUTH_FAILED: 'AUTH_GITHUB_OAUTH_FAILED',
} as const

export type AuthErrorCode =
  (typeof AUTH_ERROR_CODE)[keyof typeof AUTH_ERROR_CODE]

export type ApiErrorBody = {
  code: string
  message: string
  traceId?: string
  timestamp?: string
  details?: Record<string, unknown>
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly traceId?: string
  readonly details?: Record<string, unknown>

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.traceId = body.traceId
    this.details = body.details
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}
