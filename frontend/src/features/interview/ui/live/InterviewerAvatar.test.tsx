import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { InterviewerAvatar } from './InterviewerAvatar'

describe('InterviewerAvatar', () => {
  it('기본적으로 면접관 이미지를 렌더한다', () => {
    render(<InterviewerAvatar state="idle" />)
    expect(screen.getByAltText('면접관')).toBeInTheDocument()
  })

  it('이미지 로드 실패 시 사람 아이콘 폴백을 보여준다', () => {
    render(<InterviewerAvatar state="idle" />)
    fireEvent.error(screen.getByAltText('면접관'))
    expect(screen.queryByAltText('면접관')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: '면접관' })).toBeInTheDocument()
  })
})
