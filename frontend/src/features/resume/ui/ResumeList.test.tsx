import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ResumeList } from './ResumeList'
import type { Resume } from '../model/types'

const useResumes = vi.fn()
vi.mock('../model/useResumes', () => ({
  useResumes: () => useResumes(),
  useDeleteResume: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/shared/hooks', () => ({ useAnalysisProgress: () => null }))

const base: Resume = {
  id: 1,
  originalFilename: 'resume.pdf',
  filePath: 'resumes/raw/1/x.pdf',
  fileType: 'PDF',
  fileSize: 2048,
  sourceUrl: null,
  status: 'ANALYZED',
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
}

const webResume: Resume = {
  ...base,
  id: 2,
  originalFilename: 'my-portfolio.dev/about',
  filePath: null,
  fileType: 'WEB',
  fileSize: null,
  sourceUrl: 'https://my-portfolio.dev/about',
}

function renderWith(data: Resume[]) {
  useResumes.mockReturnValue({
    data,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  })
  return render(<ResumeList />)
}

describe('ResumeList', () => {
  it('WEB 항목은 원문으로 가는 링크로 렌더한다', () => {
    renderWith([webResume])

    const link = screen.getByRole('link', { name: 'my-portfolio.dev/about' })
    expect(link).toHaveAttribute('href', 'https://my-portfolio.dev/about')
    // 외부 링크 — 탭 탈취(reverse tabnabbing) 방지 속성이 붙어야 한다.
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByText('웹 링크')).toBeInTheDocument()
  })

  it('PDF 항목은 링크가 아니라 파일 크기를 보여준다', () => {
    renderWith([base])

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('2 KB')).toBeInTheDocument()
  })

  // fileSize 가 null 인 WEB 항목이 섞여도 목록이 깨지지 않아야 한다.
  it('파일과 링크를 한 목록에 함께 렌더한다', () => {
    renderWith([base, webResume])

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByLabelText('링크 삭제')).toBeInTheDocument()
    expect(screen.getByLabelText('이력서 삭제')).toBeInTheDocument()
  })
})
