import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@/app/styles'
import { AppProviders } from '@/app/providers'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders />
  </StrictMode>,
)
