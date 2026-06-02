import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from '@/features/auth'
import HomePage from '@/pages/Home'
import LoginPage from '@/pages/Login'
import AuthCallbackPage from '@/pages/AuthCallback'
import WorkspacePage from '@/pages/Workspace'
import InterviewSetupPage from '@/pages/InterviewSetup'
import InterviewSessionPage from '@/pages/InterviewSession'
import SessionFeedbackPage from '@/pages/SessionFeedback'
import HistoryPage from '@/pages/History'

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
    path: '/sessions/new',
    element: (
      <RequireAuth>
        <InterviewSetupPage />
      </RequireAuth>
    ),
  },
  {
    path: '/sessions/:id',
    element: (
      <RequireAuth>
        <InterviewSessionPage />
      </RequireAuth>
    ),
  },
  {
    path: '/sessions/:id/feedback',
    element: (
      <RequireAuth>
        <SessionFeedbackPage />
      </RequireAuth>
    ),
  },
  {
    path: '/history',
    element: (
      <RequireAuth>
        <HistoryPage />
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
