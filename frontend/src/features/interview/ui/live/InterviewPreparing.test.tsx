import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { Session } from '@/domain/session'
import { InterviewPreparing } from './InterviewPreparing'

const session = {
  id: 1,
  title: '백엔드 모의 면접',
  maxQuestions: 5,
  totalQuestionCount: 0,
} as Session

describe('InterviewPreparing', () => {
  it('진행 이벤트 미수신이면 기본 안내 문구를 보여준다', () => {
    render(<InterviewPreparing session={session} progressMessage={null} />)
    expect(screen.getByRole('status')).toHaveTextContent('첫 질문을 만들고 있어요')
  })

  it('QUESTION_POOL_PROGRESS 진행 문구가 오면 기본 문구를 대체한다 (B2)', () => {
    render(
      <InterviewPreparing session={session} progressMessage="자료를 바탕으로 첫 질문을 만들고 있어요." />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('자료를 바탕으로 첫 질문을 만들고 있어요.')
    expect(screen.getByRole('status')).not.toHaveTextContent('준비가 끝나면 바로 면접이 시작됩니다')
  })
})
