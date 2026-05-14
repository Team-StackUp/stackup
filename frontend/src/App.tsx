import { lazy, Suspense } from 'react'
import HomePage from '@/pages/Home'

const DesignSystemDemo = lazy(() => import('@/pages/DesignSystem'))

export default function App() {
  const path =
    typeof window !== 'undefined' ? window.location.pathname : '/'

  if (path.startsWith('/design-system')) {
    return (
      <Suspense fallback={null}>
        <DesignSystemDemo />
      </Suspense>
    )
  }

  return <HomePage />
}
