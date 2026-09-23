import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Message } from '@/domain/session'
import type { ThreadItem } from '../../model/useLiveInterview'
import { AnswerBubble } from './AnswerBubble'
import { ConversationThread } from './ConversationThread'

vi.mock('../../lib/media/useMessageAudio', () => ({
  useMessageAudio: () => ({ url: undefined, load: async () => undefined }),
}))
vi.mock('../../lib/media/useTtsPlayback', () => ({
  useTtsPlayback: () => ({ playing: false, toggle: () => {}, audioNode: null }),
}))
vi.mock('../../model/useBookmarks', () => ({
  useSetQuestionBookmark: () => ({ mutate: () => {}, isPending: false }),
}))

// 백엔드가 STT 실패 시 content 를 바꾸지 않고 sentinel 을 유지하는 경로
// (isTranscribing 이 true 여야 실패 버블로 렌더된다).
function failedVoiceAnswer(overrides: Partial<Message> = {}): Message {
  return {
    id: 200,
    sessionId: 10,
    role: 'INTERVIEWEE',
    content: '(transcribing)',
    status: 'FAILED',
    audioFilePath: 'interview/voice/raw/10/200.webm',
    sequenceNumber: 4,
    ...overrides,
  } as Message
}

describe('AnswerBubble 재전사', () => {
  it('실패한 음성 답변에 다시 인식하기 버튼을 노출하고 messageId 로 호출한다', async () => {
    const onRetranscribe = vi.fn()
    render(<AnswerBubble message={failedVoiceAnswer()} onRetranscribe={onRetranscribe} />)

    await userEvent.click(screen.getByRole('button', { name: /다시 인식하기/ }))
    expect(onRetranscribe).toHaveBeenCalledTimes(1)
  })

  it('녹음이 남아 있으면 "다시 답변해 주세요" 대신 남아 있다고 알린다', () => {
    render(<AnswerBubble message={failedVoiceAnswer()} onRetranscribe={vi.fn()} />)
    expect(screen.getByText(/녹음은 남아 있어요/)).toBeInTheDocument()
  })

  it('재전사 진행 중에는 버튼을 잠근다 — 중복 발행 방지', () => {
    render(
      <AnswerBubble message={failedVoiceAnswer()} onRetranscribe={vi.fn()} retranscribing />,
    )
    expect(screen.getByRole('button', { name: /다시 인식하는 중/ })).toBeDisabled()
  })

  it('오디오가 없는 실패(업로드 자체 실패)에는 버튼을 내지 않는다', () => {
    render(
      <AnswerBubble
        message={failedVoiceAnswer({ audioFilePath: undefined })}
        onRetranscribe={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: /다시 인식/ })).not.toBeInTheDocument()
  })

  it('전사에 성공한 답변에는 버튼을 내지 않는다', () => {
    render(
      <AnswerBubble
        message={failedVoiceAnswer({ content: '정상 전사된 답변', status: 'COMPLETED' })}
        onRetranscribe={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: /다시 인식/ })).not.toBeInTheDocument()
  })
})

describe('ConversationThread 재전사 게이팅', () => {
  it('이미 다시 답변한 뒤라면 버튼을 내지 않는다 — 뒤늦은 전사가 지나간 턴을 덮어쓴다', () => {
    const items = [
      { ...failedVoiceAnswer(), key: 'm-200' },
      {
        id: 201,
        key: 'm-201',
        sessionId: 10,
        role: 'INTERVIEWEE',
        content: '텍스트로 다시 답변',
        status: 'COMPLETED',
        sequenceNumber: 5,
      },
    ] as ThreadItem[]

    render(
      <ConversationThread items={items} awaitingQuestion={false} onRetranscribe={vi.fn()} />,
    )
    expect(screen.queryByRole('button', { name: /다시 인식/ })).not.toBeInTheDocument()
  })

  it('실패 답변이 마지막이면 버튼을 낸다', () => {
    const items = [{ ...failedVoiceAnswer(), key: 'm-200' }] as ThreadItem[]
    render(
      <ConversationThread items={items} awaitingQuestion={false} onRetranscribe={vi.fn()} />,
    )
    expect(screen.getByRole('button', { name: /다시 인식하기/ })).toBeInTheDocument()
  })
})
