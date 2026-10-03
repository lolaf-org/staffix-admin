import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'
import { AppLayout } from './layout/AppLayout'

const LoginPage = lazy(() => import('./auth/LoginPage').then((m) => ({ default: m.LoginPage })))
const OverviewPage = lazy(() => import('./pages/OverviewPage').then((m) => ({ default: m.OverviewPage })))
const MetricsPage = lazy(() => import('./pages/MetricsPage').then((m) => ({ default: m.MetricsPage })))
const LogsPage = lazy(() => import('./pages/LogsPage').then((m) => ({ default: m.LogsPage })))
const ConnectorsPage = lazy(() => import('./connectors/ConnectorsPage').then((m) => ({ default: m.ConnectorsPage })))
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
        children: [{ path: 'engines/:engineId/groups/:group/sessions/:sessionId', element: <SessionDrawer /> }],
      },
      { path: 'metrics', element: <MetricsPage /> },
      { path: 'logs', element: <LogsPage /> },
      { path: 'connectors', element: <ConnectorsPage /> },
    ],
  },
])
