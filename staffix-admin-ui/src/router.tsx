/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { lazy } from 'react'
import { createBrowserRouter } from 'react-router'
import { AppLayout } from './layout/AppLayout'

const LoginPage = lazy(() => import('./auth/LoginPage').then((m) => ({ default: m.LoginPage })))
const OverviewPage = lazy(() => import('./pages/OverviewPage').then((m) => ({ default: m.OverviewPage })))
const MetricsPage = lazy(() => import('./pages/MetricsPage').then((m) => ({ default: m.MetricsPage })))
const LogsPage = lazy(() => import('./pages/LogsPage').then((m) => ({ default: m.LogsPage })))
const TracePage = lazy(() => import('./pages/TracePage').then((m) => ({ default: m.TracePage })))
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
      { path: 'trace', element: <TracePage /> },
      { path: 'connectors', element: <ConnectorsPage /> },
    ],
  },
])
