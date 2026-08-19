import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SessionCard } from './SessionCard'
import type { Session } from '../api/historyApi'

const base: Session = {
  id: 7,
  title: '백엔드 모의면접',
  status: 'COMPLETED',
  mode: 'TECHNICAL',
  jobCategory: 'BACKEND',
  totalQuestionCount: 5,
  createdAt: '2026-08-18T00:00:00Z',
}

function renderCard(session: Partial<Session>) {
  return render(
    <MemoryRouter>
      <SessionCard session={{ ...base, ...session }} />
    </MemoryRouter>,
  )
}

describe('SessionCard', () => {
  it('완료 세션은 피드백 리포트로 간다', () => {
    renderCard({ status: 'COMPLETED' })

    expect(screen.getByRole('link')).toHaveAttribute('href', '/sessions/7/feedback')
    expect(screen.getByText('리포트 →')).toBeInTheDocument()
  })

  // 중단된 면접은 피드백이 없다. 세션 화면에서 기록을 보고 이어서 진행할 수 있다.
  it('중단 세션은 세션 화면(이어하기)으로 간다', () => {
    renderCard({ status: 'INTERRUPTED' })

    expect(screen.getByRole('link')).toHaveAttribute('href', '/sessions/7')
    expect(screen.getByText('이어하기 →')).toBeInTheDocument()
  })

  // 진행 중 면접에서 이탈했을 때 돌아갈 경로.
  it('진행 중 세션은 이어서 진행할 수 있다', () => {
    renderCard({ status: 'IN_PROGRESS' })

    expect(screen.getByRole('link')).toHaveAttribute('href', '/sessions/7')
    expect(screen.getByText('이어서 →')).toBeInTheDocument()
  })

  it('준비 상태 세션은 로비로 간다', () => {
    renderCard({ status: 'READY' })

    expect(screen.getByRole('link')).toHaveAttribute('href', '/sessions/7')
  })

  // 시작 전 취소라 보여줄 문답이 없다 — 링크를 만들지 않는다.
  it('취소 세션은 눌리지 않는다', () => {
    renderCard({ status: 'CANCELLED' })

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('취소됨')).toBeInTheDocument()
  })
})
