import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InterviewStage } from './InterviewStage'
import type { Session } from '@/domain/session'

// 스테이지는 미디어·자식 위젯이 무거워 헤더 동작만 보도록 가볍게 대체한다.
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

function renderStage(onInterrupt = vi.fn(), onEnd = vi.fn()) {
  render(
    <InterviewStage
      session={session}
      connection="open"
      items={[]}
      awaitingQuestion={false}
      questionStreaming={false}
      onSubmit={vi.fn()}
      onSubmitVoice={vi.fn()}
      voiceUploading={false}
      onEnd={onEnd}
      onInterrupt={onInterrupt}
      deliveryMode="text"
      onDeliveryModeChange={vi.fn()}
      wasSegmented={() => false}
      isSpeaking={() => false}
    />,
  )
  return { onInterrupt, onEnd }
}

describe('InterviewStage 잠시 중단', () => {
  // 종료(되돌릴 수 없음)만 있으면 잠깐 자리를 비워야 할 때 방법이 없다.
  it('중단은 확인을 거쳐야 실행된다', async () => {
    const onInterrupt = vi.fn()
    renderStage(onInterrupt)

    await userEvent.click(screen.getByRole('button', { name: '잠시 중단' }))
    expect(onInterrupt).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: '중단하기' }))
    expect(onInterrupt).toHaveBeenCalledTimes(1)
  })

  it('계속 진행을 고르면 중단하지 않는다', async () => {
    const onInterrupt = vi.fn()
    renderStage(onInterrupt)

    await userEvent.click(screen.getByRole('button', { name: '잠시 중단' }))
    await userEvent.click(screen.getByRole('button', { name: '계속 진행' }))

    expect(onInterrupt).not.toHaveBeenCalled()
  })

  // 중단과 종료는 결과가 다르다(이어하기 가능 vs 피드백 생성). 섞이면 안 된다.
  it('중단과 종료는 서로 다른 동작을 부른다', async () => {
    const onInterrupt = vi.fn()
    const onEnd = vi.fn()
    renderStage(onInterrupt, onEnd)

    // 헤더 버튼과 다이얼로그 확인 버튼의 이름이 같다 — 다이얼로그 안에서 다시 찾는다.
    await userEvent.click(screen.getByRole('button', { name: '종료' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '종료' }))

    expect(onEnd).toHaveBeenCalledTimes(1)
    expect(onInterrupt).not.toHaveBeenCalled()
  })
})
