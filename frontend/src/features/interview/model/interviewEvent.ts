export type InterviewAction =
  | { kind: 'refetch-messages' }
  | { kind: 'refetch-session' }
  | { kind: 'redirect-feedback' }
  | { kind: 'append-delta' }
  | { kind: 'queue-audio' }
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
    default:
      return { kind: 'ignore' }
  }
}
