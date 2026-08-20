import { beforeEach, describe, expect, it, vi } from 'vitest'

// 캡처 시점에 <html> 이 어떤 팔레트였는지 기록해 둔다.
let schemeDuringCapture: string | undefined
const save = vi.fn()

vi.mock('html2canvas-pro', () => ({
  default: vi.fn(async () => {
    schemeDuringCapture = document.documentElement.dataset.seedUserColorScheme
    return { width: 800, height: 600, toDataURL: () => 'data:image/png;base64,x' }
  }),
}))

vi.mock('jspdf', () => ({
  // `new jsPDF(...)` 로 호출되므로 화살표 함수는 쓸 수 없다(생성자 불가).
  jsPDF: vi.fn(function () {
    return {
      internal: { pageSize: { getWidth: () => 400, getHeight: () => 600 } },
      addImage: vi.fn(),
      addPage: vi.fn(),
      save,
    }
  }),
}))

const { downloadElementAsPdf } = await import('./downloadPdf')

describe('downloadElementAsPdf', () => {
  beforeEach(() => {
    schemeDuringCapture = undefined
    save.mockClear()
    localStorage.clear()
    document.documentElement.removeAttribute('data-seed-color-mode')
    document.documentElement.removeAttribute('data-seed-user-color-scheme')
  })

  // 다크 화면을 그대로 캡처하면 검은 배경에 밝은 글씨인 PDF 가 나온다 — 인쇄·공유용으로 못 쓴다.
  it('다크모드여도 라이트 팔레트로 캡처한다', async () => {
    localStorage.setItem('stackup.color-mode', 'dark-only')
    document.documentElement.dataset.seedColorMode = 'dark-only'
    document.documentElement.dataset.seedUserColorScheme = 'dark'

    await downloadElementAsPdf(document.createElement('div'), 'r.pdf')

    expect(schemeDuringCapture).toBe('light')
    expect(save).toHaveBeenCalledWith('r.pdf')
  })

  it('캡처가 끝나면 원래 컬러 모드로 되돌린다', async () => {
    localStorage.setItem('stackup.color-mode', 'dark-only')
    document.documentElement.dataset.seedColorMode = 'dark-only'
    document.documentElement.dataset.seedUserColorScheme = 'dark'

    await downloadElementAsPdf(document.createElement('div'), 'r.pdf')

    expect(document.documentElement.dataset.seedColorMode).toBe('dark-only')
    expect(document.documentElement.dataset.seedUserColorScheme).toBe('dark')
  })

  // 라이트 사용자는 팔레트를 건드릴 이유가 없다(불필요한 리페인트·깜빡임 방지).
  it('라이트 모드에서는 팔레트를 건드리지 않는다', async () => {
    document.documentElement.dataset.seedColorMode = 'system'
    document.documentElement.dataset.seedUserColorScheme = 'light'

    await downloadElementAsPdf(document.createElement('div'), 'r.pdf')

    expect(schemeDuringCapture).toBe('light')
    expect(document.documentElement.dataset.seedColorMode).toBe('system')
  })
})
