import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, ConfigProvider, Spin } from 'antd'
import { RouterProvider } from 'react-router'
import { isUnauthorized } from './api/client'
import { router } from './router'

const goToLoginWhenSessionExpired = (error: Error) => {
  if (isUnauthorized(error) && router.state.location.pathname !== '/login') {
    router.navigate('/login', { state: { from: router.state.location.pathname + router.state.location.search } })
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: goToLoginWhenSessionExpired }),
  mutationCache: new MutationCache({ onError: goToLoginWhenSessionExpired }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <Suspense fallback={<Spin fullscreen />}>
            <RouterProvider router={router} />
          </Suspense>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)
