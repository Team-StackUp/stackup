import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ScoreTrend } from './ScoreTrend'
import type { UserStats } from '../api/historyApi'

// recent 는 최신순(첫 항목이 가장 최근). reverse 후 시간순으로 그려진다.
const stats: UserStats = {
  totalSessionCount: 2,
  completedSessionCount: 2,
  averages: { overall: 75, technical: 73, logic: 81, communication: 75 },
  recent: [
    {
      sessionId: 2,
      overall: 80,
      technical: 75,
      logic: 82,
      communication: 78,
      endedAt: '2026-06-02T00:00:00Z',
    },
    {
      sessionId: 1,
      overall: 70,
      technical: 72,
      logic: 80,
      communication: 72,
      endedAt: '2026-06-01T00:00:00Z',
    },
  ],
}

describe('ScoreTrend', () => {
  it('4개 지표 라벨 + 최신 점수 + 지난번 대비 델타를 보여준다', () => {
    render(<ScoreTrend stats={stats} />)
    expect(screen.getByText('지표별 점수 추이 (최근 2회)')).toBeInTheDocument()
    ;['종합', '기술', '논리', '전달력'].forEach((l) =>
      expect(screen.getByText(l)).toBeInTheDocument(),
    )
    // 종합 최신 80, 지난번(70) 대비 ▲10
    expect(screen.getByText('80')).toBeInTheDocument()
    expect(screen.getByText('▲10')).toBeInTheDocument()
  })

  it('채점된 면접이 없으면 안내 문구를 보여준다', () => {
    render(<ScoreTrend stats={{ recent: [] } as UserStats} />)
    expect(screen.getByText('아직 채점된 면접이 없어요.')).toBeInTheDocument()
  })
})
