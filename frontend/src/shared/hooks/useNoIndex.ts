import { useEffect } from 'react'

/**
 * 이 페이지가 떠 있는 동안 검색엔진 색인을 거부한다.
 *
 * <p>`robots.txt` 로 크롤링을 막는 게 1차 방어지만 그건 규칙을 지키는 크롤러에게만
 * 통한다. JS 는 실행하면서 robots.txt 는 무시하는 쪽을 위한 2차 방어다.
 *
 * <p>index.html 에 정적으로 넣을 수 없다 — SPA 라 문서가 하나뿐이라서, 그렇게 하면
 * 랜딩·소개 페이지까지 통째로 색인에서 빠진다. 그래서 페이지 단위로 붙였다 뗀다.
 */
export function useNoIndex(): void {
  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'robots'
    meta.content = 'noindex, nofollow, noarchive'
    document.head.appendChild(meta)
    return () => meta.remove()
  }, [])
}
