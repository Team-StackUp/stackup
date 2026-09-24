import { describe, it, expect } from 'vitest'
import { analysisFailureMessage, isRetriableAnalysisFailure } from './analysisError'

describe('analysisFailureMessage', () => {
  it('내부 예외 문자열 대신 사람 말을 낸다', () => {
    // 운영에서 "APITimeoutError: Request timed out." 이 한글 UI 에 그대로 노출됐다.
    const msg = analysisFailureMessage('UNEXPECTED')
    expect(msg).toBe('AI 분석이 제때 끝나지 않았어요. 다시 시도해 주세요.')
    expect(msg).not.toMatch(/Error|timed out/i)
  })

  it('모르는 코드·코드 없음도 사람 말로 폴백한다', () => {
    expect(analysisFailureMessage('SOMETHING_NEW')).toBe(
      '분석에 실패했어요. 다시 시도해 주세요.',
    )
    expect(analysisFailureMessage(null)).toBe('분석에 실패했어요. 다시 시도해 주세요.')
  })

  it('영문 예외 문구가 새어 나가지 않는다', () => {
    for (const code of ['UNEXPECTED', 'GEMINI_RATE_LIMITED', 'WEB_FETCH_FAILED', null]) {
      expect(analysisFailureMessage(code)).not.toMatch(/[A-Za-z]{6,}Error/)
    }
  })
})

describe('isRetriableAnalysisFailure', () => {
  it('일시 장애는 재시도를 권한다', () => {
    expect(isRetriableAnalysisFailure('UNEXPECTED')).toBe(true)
    expect(isRetriableAnalysisFailure('GEMINI_RATE_LIMITED')).toBe(true)
    expect(isRetriableAnalysisFailure('WEB_FETCH_FAILED')).toBe(true)
  })

  it('자료 자체가 문제면 재시도 버튼을 내지 않는다 — 눌러도 같은 자리로 돌아온다', () => {
    expect(isRetriableAnalysisFailure('EMPTY_PDF_TEXT')).toBe(false)
    expect(isRetriableAnalysisFailure('BLOCKED_WEB_URL')).toBe(false)
    expect(isRetriableAnalysisFailure('WEB_HOST_UNRESOLVED')).toBe(false)
  })

  it('알 수 없는 코드는 일단 재시도를 허용한다', () => {
    expect(isRetriableAnalysisFailure('SOMETHING_NEW')).toBe(true)
    expect(isRetriableAnalysisFailure(null)).toBe(true)
  })
})
