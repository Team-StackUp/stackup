export type InterviewAction =
  | { kind: 'refetch-messages' }
  | { kind: 'refetch-session' }
  | { kind: 'redirect-feedback' }
  | { kind: 'ignore' }

export function interviewEventAction(event: string): InterviewAction {
  switch (event) {
    case 'session.message':
      return { kind: 'refetch-messages' }
    case 'session.state':
      return { kind: 'refetch-session' }
    case 'feedback.ready':
      return { kind: 'redirect-feedback' }
    default:
      return { kind: 'ignore' }
  }
}
