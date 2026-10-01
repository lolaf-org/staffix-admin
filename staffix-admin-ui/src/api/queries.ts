import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App } from 'antd'
import { api, call, csrfHeader, ApiError, type Schemas } from './client'
import type { paths } from './schema'

const POLL_INTERVAL_MS = 2000

export function useCurrentUser() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => call(api.GET('/api/me')),
    retry: false,
    staleTime: Infinity,
  })
}

export function useCanWrite(): boolean {
  return useCurrentUser().data?.roles.includes('WRITER') ?? false
}

export function useEngines() {
  return useQuery({
    queryKey: ['engines'],
    queryFn: () => call(api.GET('/api/engines')),
    refetchInterval: POLL_INTERVAL_MS,
  })
}

export function useSessions(instanceId: string | undefined) {
  return useQuery({
    queryKey: ['sessions', instanceId],
    queryFn: () => call(api.GET('/api/engines/{instanceId}/sessions', { params: { path: { instanceId: instanceId! } } })),
    refetchInterval: POLL_INTERVAL_MS,
    enabled: instanceId !== undefined,
  })
}

export function useSession(instanceId: string, sessionId: string) {
  return useQuery({
    queryKey: ['sessions', instanceId, sessionId],
    queryFn: () =>
      call(api.GET('/api/engines/{instanceId}/sessions/{sessionId}', { params: { path: { instanceId, sessionId } } })),
    refetchInterval: POLL_INTERVAL_MS,
    retry: false,
  })
}

export function useSessionSettings(instanceId: string, sessionId: string) {
  return useQuery({
    queryKey: ['settings', instanceId, sessionId],
    queryFn: () =>
      call(
        api.GET('/api/engines/{instanceId}/sessions/{sessionId}/settings', {
          params: { path: { instanceId, sessionId } },
        }),
      ),
  })
}

/**
 * A state-changing call on an engine: reports the outcome to the operator and refreshes that engine's sessions.
 */
export function useEngineMutation<V>(instanceId: string, mutationFn: (variables: V) => Promise<unknown>, success: string) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      message.success(success)
      return queryClient.invalidateQueries({ queryKey: ['sessions', instanceId] })
    },
    onError: (error) => message.error(error.message),
  })
}

export async function login(username: string, password: string): Promise<void> {
  const response = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', ...csrfHeader() },
    body: new URLSearchParams({ username, password }),
  })
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 401 ? 'Wrong username or password' : 'Login failed')
  }
}

export async function logout(): Promise<void> {
  await fetch('/api/logout', { method: 'POST', headers: csrfHeader() })
}

export type MetricKind = paths['/api/metrics']['get']['parameters']['query']['kind']
export type LogType = NonNullable<Schemas['LogEntry']['type']>

function window(minutes: number) {
  const to = new Date()
  return { from: new Date(to.getTime() - minutes * 60_000).toISOString(), to: to.toISOString() }
}

export function useMetrics(instanceId: string, sessionId: string, kind: MetricKind, quantile: number, minutes: number) {
  return useQuery({
    queryKey: ['metrics', instanceId, sessionId, kind, quantile, minutes],
    queryFn: () =>
      call(api.GET('/api/metrics', { params: { query: { instanceId, sessionId, kind, quantile, ...window(minutes) } } })),
    refetchInterval: 15_000,
    placeholderData: (previous) => previous,
  })
}

export interface LogSearch {
  instanceId: string
  sessionId?: string
  minutes: number
  text?: string
  types: LogType[]
  limit: number
}

export function useLogs(search: LogSearch, enabled: boolean) {
  return useQuery({
    queryKey: ['logs', search],
    queryFn: () =>
      call(
        api.GET('/api/logs', {
          params: {
            query: {
              instanceId: search.instanceId,
              sessionId: search.sessionId,
              text: search.text || undefined,
              types: search.types,
              limit: search.limit,
              ...window(search.minutes),
            },
          },
        }),
      ),
    enabled,
  })
}
