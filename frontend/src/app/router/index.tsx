import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from '@/features/auth'
import HomePage from '@/pages/Home'
import LoginPage from '@/pages/Login'
import AuthCallbackPage from '@/pages/AuthCallback'
import WorkspacePage from '@/pages/Workspace'

export const router = createBrowserRouter([
  { path: '/', element: <HomePage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/auth/callback', element: <AuthCallbackPage /> },
  {
    path: '/workspace',
    element: (
      <RequireAuth>
        <WorkspacePage />
      </RequireAuth>
    ),
  },
  {
    path: '/design-system/*',
    lazy: async () => {
      const mod = await import('@/pages/DesignSystem')
      return { Component: mod.default }
    },
  },
])
