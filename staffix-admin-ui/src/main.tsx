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
import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp, Spin } from 'antd'
import { RouterProvider } from 'react-router'
import { isUnauthorized } from './api/client'
import { router } from './router'
import { ThemeModeProvider } from './theme/ThemeMode'

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
    <ThemeModeProvider>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <Suspense fallback={<Spin fullscreen />}>
            <RouterProvider router={router} />
          </Suspense>
        </QueryClientProvider>
      </AntApp>
    </ThemeModeProvider>
  </StrictMode>,
)
