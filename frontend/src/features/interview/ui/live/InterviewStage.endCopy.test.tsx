import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InterviewStage } from './InterviewStage'
import type { Message, Session } from '@/domain/session'

vi.mock('./InterviewerAvatar', () => ({ InterviewerAvatar: () => <div /> }))
vi.mock('./StageQuestion', () => ({ StageQuestion: () => <div /> }))
vi.mock('./WebcamSelfView', () => ({ WebcamSelfView: () => <div /> }))
vi.mock('./TranscriptDrawer', () => ({ TranscriptDrawer: () => <div /> }))
vi.mock('./AnswerComposer', () => ({ AnswerComposer: () => <div /> }))
vi.mock('./DeliveryModeToggle', () => ({ DeliveryModeToggle: () => <div /> }))

const session: Session = {
  id: 7,
  title: '백엔드 모의면접',
  status: 'IN_PROGRESS',
  maxQuestions: 5,
  generalQuestionCount: 5,
  totalQuestionCount: 2,
}

const question: Message = { id: 1, sequenceNumber: 1, role: 'INTERVIEWER', content: '자기소개' }
const answer: Message = { id: 2, sequenceNumber: 2, role: 'INTERVIEWEE', content: '안녕하세요' }

function renderStage(items: Message[]) {
  render(
    <InterviewStage
      session={session}
      connection="open"
      items={items.map((m) => ({ ...m, key: `m-${m.id}` }))}
      awaitingQuestion={false}
      questionStreaming={false}
      onSubmit={vi.fn()}
      onSubmitVoice={vi.fn()}
      voiceUploading={false}
      onEnd={vi.fn()}
      onInterrupt={vi.fn()}
      deliveryMode="text"
      onDeliveryModeChange={vi.fn()}
      wasSegmented={() => false}
      isSpeaking={() => false}
      onRetranscribe={vi.fn()}
      retranscribing={false}
    />,
  )
}

// 서버는 답변이 0개인 세션을 COMPLETED 가 아니라 INTERRUPTED 로 끝낸다(SessionService.end).
// 그 경우 "피드백 단계로 넘어갑니다 / 되돌릴 수 없습니다" 는 둘 다 거짓이 된다.
describe('InterviewStage 종료 안내 문구', () => {
  it('답변이 있으면 피드백 단계로 넘어간다고 안내한다', async () => {
    renderStage([question, answer])

    await userEvent.click(screen.getByRole('button', { name: '종료' }))

    expect(screen.getByText(/피드백 단계로 넘어갑니다/)).toBeTruthy()
  })

  it('답변이 없으면 피드백을 약속하지 않고 이어서 할 수 있다고 안내한다', async () => {
    renderStage([question])

    await userEvent.click(screen.getByRole('button', { name: '종료' }))

    expect(screen.getByText(/피드백은 만들어지지 않습니다/)).toBeTruthy()
    expect(screen.queryByText(/되돌릴 수 없습니다/)).toBeNull()
  })
})
