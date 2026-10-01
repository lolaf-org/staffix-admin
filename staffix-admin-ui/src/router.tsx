import { createBrowserRouter } from 'react-router'
import { AppLayout } from './layout/AppLayout'
import { OverviewPage } from './pages/OverviewPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [{ index: true, element: <OverviewPage /> }],
  },
])
