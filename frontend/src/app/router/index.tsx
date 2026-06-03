import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from '@/features/auth'
import { ScrollToTop } from './ScrollToTop'
import HomePage from '@/pages/Home'
import LoginPage from '@/pages/Login'
import AuthCallbackPage from '@/pages/AuthCallback'
import WorkspacePage from '@/pages/Workspace'
import InterviewSetupPage from '@/pages/InterviewSetup'
import InterviewSessionPage from '@/pages/InterviewSession'
import PracticePage from '@/pages/Practice'
import SessionFeedbackPage from '@/pages/SessionFeedback'
import SharedFeedbackPage from '@/pages/SharedFeedback'

export const router = createBrowserRouter([
  {
    element: <ScrollToTop />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/practice/:track', element: <PracticePage /> },
      { path: '/share/:token', element: <SharedFeedbackPage /> },
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
        path: '/workspace/resumes',
        element: (
          <RequireAuth>
            <WorkspacePage />
          </RequireAuth>
        ),
      },
      {
        path: '/workspace/repos',
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
        path: '/workspace/history',
        element: (
          <RequireAuth>
            <WorkspacePage />
          </RequireAuth>
        ),
      },
      { path: '/history', element: <Navigate to="/workspace/history" replace /> },
      {
        path: '/design-system/*',
        lazy: async () => {
          const mod = await import('@/pages/DesignSystem')
          return { Component: mod.default }
        },
      },
    ],
  },
])
