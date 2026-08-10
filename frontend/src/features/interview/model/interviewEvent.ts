export type InterviewAction =
  | { kind: 'refetch-messages' }
  | { kind: 'refetch-session' }
  | { kind: 'redirect-feedback' }
  | { kind: 'append-delta' }
  | { kind: 'queue-audio' }
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
    // realtime 서버는 제출 실패 시 'error' 프레임을 보낸다(ws.go outboundFrame).
    // 백엔드 SseEventType.ERROR 도 동일 취급. 무시하면 거부된 답변이 영구 '전송 중'으로 남는다.
    case 'error':
    case 'ERROR':
      return { kind: 'rollback-optimistic' }
    default:
      return { kind: 'ignore' }
  }
}
