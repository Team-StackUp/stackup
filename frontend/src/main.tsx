import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/app/styles'
import { AppProviders } from '@/app/providers'
import { applyColorMode, readStoredColorMode } from '@/shared/lib/color-mode'

// 첫 페인트 전에 <html> 에 컬러 모드를 반영한다 — React 마운트를 기다리면
// 다크 사용자에게 라이트 화면이 한 프레임 번쩍인다.
applyColorMode(readStoredColorMode())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders />
  </StrictMode>,
)
