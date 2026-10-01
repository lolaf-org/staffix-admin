import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'
import { AppLayout } from './layout/AppLayout'

const LoginPage = lazy(() => import('./auth/LoginPage').then((m) => ({ default: m.LoginPage })))
const OverviewPage = lazy(() => import('./pages/OverviewPage').then((m) => ({ default: m.OverviewPage })))
const LogsPage = lazy(() => import('./pages/LogsPage').then((m) => ({ default: m.LogsPage })))
const SessionDrawer = lazy(() => import('./sessions/SessionDrawer').then((m) => ({ default: m.SessionDrawer })))

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        path: '',
        element: <OverviewPage />,
        children: [{ path: 'engines/:instanceId/sessions/:sessionId', element: <SessionDrawer /> }],
      },
      { path: 'logs', element: <LogsPage /> },
    ],
  },
])
