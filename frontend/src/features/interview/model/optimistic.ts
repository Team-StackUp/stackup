import type { Message } from '@/domain/session'

export type OptimisticAnswer = { tempId: string; content: string }

// 서버 messages 에 같은 내용의 INTERVIEWEE 답변이 반영된 만큼만 낙관적 항목을 제거한다.
// content 가 같은 답변이 여러 번 제출돼도 서버 메시지 1개당 낙관적 1개씩만 소진(1:1)되도록
// 카운트로 매칭한다 — 동일 내용 답변이 한꺼번에 사라지는 문제 방지.
export function pendingAnswers(
  optimistic: OptimisticAnswer[],
  serverMessages: Message[],
): OptimisticAnswer[] {
  const remaining = new Map<string, number>()
  for (const m of serverMessages) {
    if (m.role === 'INTERVIEWEE' && typeof m.content === 'string') {
      remaining.set(m.content, (remaining.get(m.content) ?? 0) + 1)
    }
  }
  const pending: OptimisticAnswer[] = []
  for (const o of optimistic) {
    const count = remaining.get(o.content) ?? 0
    if (count > 0) {
      remaining.set(o.content, count - 1) // 서버 메시지 하나로 소진
    } else {
      pending.push(o)
    }
  }
  return pending
}

export function toOptimisticMessage(answer: OptimisticAnswer): Message {
  return { role: 'INTERVIEWEE', content: answer.content, status: 'COMPLETED' } as Message
}
