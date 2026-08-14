import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { InterviewSetupForm } from './InterviewSetupForm'

// 폼 안의 ContextDocumentPicker 가 '자료 준비하기' 링크(react-router Link)를 렌더한다.
// Router 없이 render 하면 basename 컨텍스트가 없어 폼 전체가 throw 한다.
function renderForm(ui: React.ReactElement) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('InterviewSetupForm', () => {
  it('모드·직군 선택 전에는 생성 버튼이 비활성', () => {
    renderForm(<InterviewSetupForm documents={[]} onCreate={vi.fn()} />)
    expect(screen.getByRole('button', { name: '면접 생성' })).toBeDisabled()
  })

  it('모드·직군(복수) 선택 후 생성하면 요청을 만든다', async () => {
    const onCreate = vi.fn()
    renderForm(<InterviewSetupForm documents={[]} onCreate={onCreate} />)
    await userEvent.click(screen.getByRole('radio', { name: '기술 면접' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '백엔드' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '프론트엔드' }))
    await userEvent.click(screen.getByRole('button', { name: '면접 생성' }))
    expect(onCreate).toHaveBeenCalledWith({
      mode: 'TECHNICAL',
      jobCategories: ['BACKEND', 'FRONTEND'],
      generalQuestionCount: 3,
      maxFollowupsPerQuestion: 2,
      maxQuestions: 10,
      contextDocumentIds: [],
    })
  })

  it('직무 맞춤 모드는 JD 입력이 노출되고, JD 없으면 생성 비활성', async () => {
    renderForm(<InterviewSetupForm documents={[]} onCreate={vi.fn()} />)
    // 다른 모드에선 JD 입력이 없다.
    expect(screen.queryByLabelText(/채용공고/)).toBeNull()

    await userEvent.click(screen.getByRole('radio', { name: '직무 맞춤 면접' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '백엔드' }))

    // JD 입력이 노출되고, 아직 비어 있어 생성은 비활성.
    expect(screen.getByLabelText(/채용공고/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '면접 생성' })).toBeDisabled()
  })

  it('직무 맞춤 모드는 회사·JD를 요청에 담는다', async () => {
    const onCreate = vi.fn()
    renderForm(<InterviewSetupForm documents={[]} onCreate={onCreate} />)
    await userEvent.click(screen.getByRole('radio', { name: '직무 맞춤 면접' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '백엔드' }))
    await userEvent.type(screen.getByLabelText(/회사명/), '토스')
    await userEvent.type(
      screen.getByLabelText(/채용공고/),
      'Kotlin/Spring 백엔드, 대용량 결제',
    )
    await userEvent.click(screen.getByRole('button', { name: '면접 생성' }))

    expect(onCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        mode: 'JOB_TAILORED',
        jobCategories: ['BACKEND'],
        targetCompanyName: '토스',
        targetJobDescription: 'Kotlin/Spring 백엔드, 대용량 결제',
      }),
    )
  })
})
