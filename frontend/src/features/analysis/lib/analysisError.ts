// 분석 실패 사유를 사람 말로. 백엔드가 errorMessage 에 실어 보내는 건 AI 서버의 예외 문자열이라
// (운영에서 "APITimeoutError: Request timed out." 이 한글 UI 에 그대로 노출됐다) 화면에는
// errorCode 로 고른 문구만 쓴다. 원문은 서버 로그·지원 문의용으로만 남긴다.
const MESSAGE: Record<string, string> = {
  // 일시 장애 — 다시 시도하면 대체로 풀린다
  UNEXPECTED: 'AI 분석이 제때 끝나지 않았어요. 다시 시도해 주세요.',
  GEMINI_RATE_LIMITED: '요청이 몰려 분석이 밀렸어요. 잠시 후 다시 시도해 주세요.',
  WEB_FETCH_FAILED: '사이트를 불러오지 못했어요. 주소가 열려 있는지 확인하고 다시 시도해 주세요.',
  REPO_AUTH_OR_RATE: 'GitHub 요청 한도에 걸렸어요. 잠시 후 다시 시도해 주세요.',
  EMBED_COUNT_MISMATCH: '분석 결과를 저장하는 중 문제가 생겼어요. 다시 시도해 주세요.',

  // 자료 자체의 문제 — 다시 시도해도 같다
  EMPTY_PDF_TEXT: '파일에서 글자를 찾지 못했어요. 스캔 이미지라면 텍스트 PDF 로 다시 올려 주세요.',
  EMPTY_WEB_BODY: '페이지에서 읽을 내용을 찾지 못했어요.',
  EMPTY_REPO_CONTENT: '레포지토리에서 분석할 코드를 찾지 못했어요.',
  WEB_NOT_HTML: '웹페이지가 아니라 분석할 수 없어요.',
  WEB_HTML_TOO_LARGE: '페이지가 너무 커서 분석할 수 없어요.',
  WEB_HTTP_STATUS: '사이트가 오류를 돌려줬어요. 주소를 확인해 주세요.',
  WEB_HOST_UNRESOLVED: '주소를 찾을 수 없어요. 오타가 없는지 확인해 주세요.',
  WEB_TOO_MANY_REDIRECTS: '주소가 너무 여러 번 이동해 분석할 수 없어요.',
  INVALID_WEB_URL: '지원하지 않는 주소 형식이에요.',
  BLOCKED_WEB_URL: '접근할 수 없는 주소예요.',
  REPO_NOT_FOUND: '레포지토리를 찾을 수 없어요. 접근 권한을 확인해 주세요.',
  INVALID_REPO_LOCATOR: '레포지토리 주소를 알아볼 수 없어요.',
  DOCUMENT_NOT_FOUND: '원본 파일을 찾을 수 없어요.',
}

// 다시 시도해도 같은 결과가 나오는 실패. 이 경우 재분석 버튼을 내지 않는다 —
// 눌러도 같은 자리로 돌아오면 사용자는 고장으로 받아들인다.
const PERMANENT = new Set([
  'EMPTY_PDF_TEXT',
  'EMPTY_WEB_BODY',
  'EMPTY_REPO_CONTENT',
  'WEB_NOT_HTML',
  'WEB_HTML_TOO_LARGE',
  'WEB_HOST_UNRESOLVED',
  'WEB_TOO_MANY_REDIRECTS',
  'INVALID_WEB_URL',
  'BLOCKED_WEB_URL',
  'INVALID_REPO_LOCATOR',
  'DOCUMENT_NOT_FOUND',
])

export function analysisFailureMessage(errorCode?: string | null): string {
  return (errorCode && MESSAGE[errorCode]) || '분석에 실패했어요. 다시 시도해 주세요.'
}

export function isRetriableAnalysisFailure(errorCode?: string | null): boolean {
  return !errorCode || !PERMANENT.has(errorCode)
}
