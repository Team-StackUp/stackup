import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnswerComposer } from './AnswerComposer'

// 녹음 훅은 실제 마이크를 잡으므로 상태만 흉내낸다.
const recorder = {
  status: 'idle' as string,
  stream: null,
  start: vi.fn(async () => true),
  stop: vi.fn(async () => null),
  cancel: vi.fn(),
}
vi.mock('../../lib/media/useVoiceRecorder', () => ({
  useVoiceRecorder: () => recorder,
}))
vi.mock('./MicLevelMeter', () => ({ MicLevelMeter: () => <div /> }))

beforeEach(() => {
  recorder.status = 'idle'
  recorder.cancel.mockClear()
  recorder.start.mockClear()
})

function renderComposer(props: Partial<Parameters<typeof AnswerComposer>[0]> = {}) {
  return render(
    <AnswerComposer onSubmit={vi.fn()} onSubmitVoice={vi.fn()} {...props} />,
  )
}

describe('AnswerComposer 음성 답변', () => {
  // 권한 프롬프트를 놓치면 getUserMedia 가 영원히 pending 이다. 그동안 텍스트 입력창이
  // 사라지므로, 빠져나갈 길이 없으면 답변 자체가 막힌다.
  it('마이크 권한 대기 중에도 텍스트로 돌아갈 수 있다', async () => {
    recorder.status = 'requesting'
    renderComposer()

    const escape = screen.getByRole('button', { name: '텍스트로 답변' })
    await userEvent.click(escape)

    expect(recorder.cancel).toHaveBeenCalled()
  })

  it('권한 대기 중에는 전송 버튼을 노출하지 않는다', () => {
    recorder.status = 'requesting'
    renderComposer()

    expect(screen.queryByRole('button', { name: '전송' })).not.toBeInTheDocument()
  })

  it('녹음 중에는 취소와 전송을 모두 제공한다', () => {
    recorder.status = 'recording'
    renderComposer()

    expect(screen.getByRole('button', { name: '취소' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전송' })).toBeInTheDocument()
  })

  // 연결이 끊겨 막힌 것을 '질문을 기다리는 중' 으로 적으면 AI 가 느린 줄 안다.
  it('연결 끊김과 질문 대기를 다른 문구로 안내한다', () => {
    const { unmount } = renderComposer({ disabled: true, disabledReason: 'disconnected' })
    expect(screen.getByLabelText('답변 입력')).toHaveAttribute(
      'placeholder',
      expect.stringContaining('연결이 끊겨'),
    )
    unmount()

    renderComposer({ disabled: true, disabledReason: 'awaiting-question' })
    expect(screen.getByLabelText('답변 입력')).toHaveAttribute(
      'placeholder',
      '질문을 기다리는 중…',
    )
  })

  it('마이크 권한이 거부되면 텍스트 전용 안내를 보여준다', () => {
    recorder.status = 'denied'
    renderComposer()

    expect(screen.getByText(/마이크 권한이 거부되어/)).toBeInTheDocument()
  })
})
