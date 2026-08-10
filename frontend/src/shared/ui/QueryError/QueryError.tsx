export type QueryErrorProps = {
  /** 사용자에게 보여줄 한국어 안내. 서버 raw message 를 넘기지 말 것 — 내부 문구가 그대로 노출된다. */
  message: string
  onRetry?: () => void
  className?: string
}

/**
 * 목록/상세 쿼리 실패 상태의 표준 표현 — 안내 문구 + 다시 시도.
 *
 * 기존에 각 feature 가 `isApiError(error) ? error.message : …` 로 서버 메시지를
 * 그대로 보여줬는데, 500 계열은 영어 원문("server error")이 노출되고 재시도 수단도
 * 없었다. 에러 문구는 항상 우리가 쓴 한국어 안내로 고정하고 재시도를 붙인다.
 */
export function QueryError({ message, onRetry, className = '' }: QueryErrorProps) {
  return (
    <div className={`flex flex-col items-center gap-2 py-8 text-center ${className}`}>
      <p className="text-body text-fg-muted">{message}</p>
      {onRetry ? (
        <button
          type="button"
          className="text-caption text-primary-fg underline underline-offset-2"
          onClick={onRetry}
        >
          다시 시도
        </button>
      ) : null}
    </div>
  )
}
