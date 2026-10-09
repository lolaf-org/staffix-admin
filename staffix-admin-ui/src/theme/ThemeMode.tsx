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
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { ConfigProvider, theme } from 'antd'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'staffix-admin.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

interface ThemeModeState {
  mode: ThemeMode
  dark: boolean
  setMode: (mode: ThemeMode) => void
}

const ThemeModeContext = createContext<ThemeModeState>({ mode: 'system', dark: false, setMode: () => {} })

export function useThemeMode(): ThemeModeState {
  return useContext(ThemeModeContext)
}

/**
 * Light, dark, or the operating system's choice, remembered per browser.
 */
export function ThemeModeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia(DARK_QUERY).matches)

  useEffect(() => {
    const query = window.matchMedia(DARK_QUERY)
    const follow = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    query.addEventListener('change', follow)
    return () => query.removeEventListener('change', follow)
  }, [])

  const dark = mode === 'dark' || (mode === 'system' && systemDark)

  useEffect(() => {
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
  }, [dark])

  const setMode = (next: ThemeMode) => {
    setModeState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // storage refused (private window): the choice lasts until reload
    }
  }

  return (
    <ThemeModeContext.Provider value={{ mode, dark, setMode }}>
      <ConfigProvider theme={{ algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm }}>
        <BodyBackground />
        {children}
      </ConfigProvider>
    </ThemeModeContext.Provider>
  )
}

/**
 * Ant Design paints its components, not the page: the login page sits on the bare body.
 */
function BodyBackground() {
  const { token } = theme.useToken()
  useEffect(() => {
    document.body.style.background = token.colorBgLayout
  }, [token.colorBgLayout])
  return null
}

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    return 'system'
  }
}
