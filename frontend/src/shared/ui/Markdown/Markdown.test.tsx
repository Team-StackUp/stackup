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

  // 위 케이스는 react-markdown 이 raw HTML 을 escape 하므로 rehype-sanitize 가 없어도 통과한다.
  // 반면 링크 protocol 필터링은 **오직 rehype-sanitize 만** 막아준다 — 즉 이 케이스가
  // sanitize 플러그인이 실제로 붙어 동작하는지 검증하는 유일한 지점이다.
  // 렌더 대상(분석 문서 마크다운·answerRewrite)은 사용자 이력서·답변에서 파생되므로,
  // 스키마를 커스터마이즈하거나 플러그인을 빼는 변경이 조용히 통과하면 안 된다.
  it.each([
    ['javascript:', '[클릭](javascript:alert(1))'],
    ['대소문자 우회', '[클릭](JaVaScRiPt:alert(1))'],
    ['data: HTML', '[클릭](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)'],
  ])('%s 링크는 href 가 제거된다', async (_label, markdown) => {
    const { container } = render(<Markdown>{markdown}</Markdown>)

    await screen.findByText('클릭')
    // 링크 텍스트는 남기되 이동 가능한 href 는 남기지 않는다.
    expect(container.querySelector('a')?.getAttribute('href') ?? null).toBeNull()
  })

  it('일반 http(s) 링크는 그대로 살린다 — 필터가 과하게 걷어내지 않는지', async () => {
    const { container } = render(<Markdown>{'[문서](https://example.com/a)'}</Markdown>)

    await screen.findByText('문서')
    expect(container.querySelector('a')).toHaveAttribute('href', 'https://example.com/a')
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
