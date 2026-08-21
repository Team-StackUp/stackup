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
import NotFoundPage from '@/pages/NotFound'
import { RouteError } from './RouteError'

export const router = createBrowserRouter([
  {
    element: <ScrollToTop />,
    // 렌더 중 throw 를 백지 대신 복구 화면으로 — 규약 §10(404/500) 대응.
    errorElement: <RouteError />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/practice/:track', element: <PracticePage /> },
      { path: '/share/:token', element: <SharedFeedbackPage /> },
      { path: '/auth/callback', element: <AuthCallbackPage /> },
      { path: '/auth/google/callback', element: <AuthCallbackPage /> },
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
        path: '/workspace/cover-letters',
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
      {
        path: '/workspace/bookmarks',
        element: (
          <RequireAuth>
            <WorkspacePage />
          </RequireAuth>
        ),
      },
      {
        path: '/workspace/account',
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
      // catch-all — 오타 URL 이 react-router 기본 에러 화면으로 새지 않게 한다.
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
