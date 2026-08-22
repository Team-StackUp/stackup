import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Markdown } from './Markdown'

describe('Markdown', () => {
  it('lazy 로딩 사이에는 plain text fallback 을 보여준다 — 내용 공백이 없다', () => {
    // 반드시 파일의 첫 테스트여야 한다 — lazy 모듈이 한 번 로드되면 이후엔 동기 렌더된다.
    render(<Markdown>{'**핵심** 문장'}</Markdown>)
    expect(screen.getByText('**핵심** 문장')).toBeInTheDocument()
  })

  it('GFM 마크다운을 렌더한다 — 굵게·리스트·코드가 원문 기호로 노출되지 않는다', async () => {
    render(
      <Markdown>{'**핵심**을 지키세요.\n\n- 첫째\n- 둘째\n\n`useMemo` 사용'}</Markdown>,
    )

    // lazy 로더가 풀린 뒤 실제 엘리먼트로 렌더된다.
    const strong = await screen.findByText('핵심')
    expect(strong.tagName).toBe('STRONG')
    expect(screen.getByRole('list')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    const code = screen.getByText('useMemo')
    expect(code.tagName).toBe('CODE')
    // 원문 기호가 텍스트로 새지 않는다.
    expect(screen.queryByText(/\*\*/)).toBeNull()
  })

  it('raw HTML(script 포함)은 실행 가능한 요소로 렌더되지 않는다', async () => {
    const { container } = render(
      <Markdown>{'안전 확인 <script>window.__pwned = true</script> <img src=x onerror="window.__pwned=true" /> 끝'}</Markdown>,
    )

    await screen.findByText(/안전 확인/)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined()
  })

  it('코드 블록은 pre 로 감싸 가로 스크롤 컨테이너에 렌더된다', async () => {
    const { container } = render(
      <Markdown>{'```java\n@Transactional\npublic void end() {}\n```'}</Markdown>,
    )

    await screen.findByText(/Transactional/)
    const pre = container.querySelector('pre')
    expect(pre).not.toBeNull()
    expect(pre?.className).toContain('overflow-x-auto')
  })
})
