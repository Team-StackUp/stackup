export type InterviewAction =
  | { kind: 'refetch-messages' }
  | { kind: 'refetch-session' }
  | { kind: 'redirect-feedback' }
  | { kind: 'append-delta' }
  | { kind: 'queue-audio' }
  | { kind: 'pool-progress' }
  | { kind: 'rollback-optimistic' }
  | { kind: 'ignore' }

export function interviewEventAction(event: string): InterviewAction {
  switch (event) {
    case 'SESSION_MESSAGE':
      return { kind: 'refetch-messages' }
    case 'SESSION_STATE':
      return { kind: 'refetch-session' }
    case 'FEEDBACK_READY':
      return { kind: 'redirect-feedback' }
    case 'SESSION_MESSAGE_DELTA':
      return { kind: 'append-delta' }
    case 'SESSION_MESSAGE_AUDIO':
      return { kind: 'queue-audio' }
    // AI 가 질문 풀 생성 단계를 세션 채널로 직접 발행(휘발성) — 대기 화면 진행 문구용.
    case 'QUESTION_POOL_PROGRESS':
      return { kind: 'pool-progress' }
    // realtime 서버는 제출 실패 시 'error' 프레임을 보낸다(ws.go outboundFrame).
    // 백엔드 SseEventType.ERROR 도 동일 취급. 무시하면 거부된 답변이 영구 '전송 중'으로 남는다.
    case 'error':
    case 'ERROR':
      return { kind: 'rollback-optimistic' }
    default:
      return { kind: 'ignore' }
  }
}
