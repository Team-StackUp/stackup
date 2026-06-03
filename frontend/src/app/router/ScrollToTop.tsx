import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'

// createBrowserRouter 는 라우트 이동 시 스크롤을 자동으로 초기화하지 않는다.
// 푸터 등 페이지 하단 링크로 이동하면 직전 스크롤 위치가 그대로 유지돼
// 새 페이지에서도 하단(푸터)을 보게 되므로, 경로 변경 시 상단으로 올린다.
// 해시 앵커(/#services 등)는 각 페이지가 직접 스크롤하므로 건드리지 않는다.
export function ScrollToTop() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    if (hash) return
    window.scrollTo({ top: 0 })
  }, [pathname, hash])

  return <Outlet />
}
