export type InterviewAction =
  | { kind: 'refetch-messages' }
  | { kind: 'refetch-session' }
  | { kind: 'redirect-feedback' }
  | { kind: 'ignore' }

export function interviewEventAction(event: string): InterviewAction {
  switch (event) {
    case 'SESSION_MESSAGE':
      return { kind: 'refetch-messages' }
    case 'SESSION_STATE':
      return { kind: 'refetch-session' }
    case 'FEEDBACK_READY':
      return { kind: 'redirect-feedback' }
    default:
      return { kind: 'ignore' }
  }
}
