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

/**
 * Session ids are unique within their group only, so a session is always named by both.
 */
export interface SessionRef {
  engineId: string
  group: string
  sessionId: string
}

export function sessionPath(ref: SessionRef) {
  return { params: { path: ref } }
}

export function sessionRoute(ref: SessionRef): string {
  return `/engines/${encodeURIComponent(ref.engineId)}/groups/${encodeURIComponent(ref.group)}/sessions/${encodeURIComponent(ref.sessionId)}`
}

export function useGroups(engineId: string | undefined) {
  return useQuery({
    queryKey: ['groups', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/groups', { params: { path: { engineId: engineId! } } })),
    refetchInterval: POLL_INTERVAL_MS,
    enabled: engineId !== undefined,
  })
}

export function useSession(ref: SessionRef) {
  return useQuery({
    queryKey: ['groups', ref.engineId, ref.group, ref.sessionId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}', sessionPath(ref))),
    refetchInterval: POLL_INTERVAL_MS,
    retry: false,
  })
}

export function useSessionSettings(ref: SessionRef, runningConfigId: string) {
  return useQuery({
    queryKey: ['settings', ref.engineId, ref.group, ref.sessionId, runningConfigId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', sessionPath(ref))),
  })
}

/**
 * A state-changing call on an engine: reports the outcome to the operator and refreshes that engine's sessions.
 */
export function useEngineMutation<V>(engineId: string, mutationFn: (variables: V) => Promise<unknown>, success: string) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      message.success(success)
      return queryClient.invalidateQueries({ queryKey: ['groups', engineId] })
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

/**
 * An engine, one of its groups, or one config of a group.
 */
export interface MetricScope {
  engineId: string
  group?: string
  sessionId?: string
}

export function useMetrics(scope: MetricScope, kind: MetricKind, quantile: number, minutes: number) {
  return useQuery({
    queryKey: ['metrics', scope, kind, quantile, minutes],
    queryFn: () => call(api.GET('/api/metrics', { params: { query: { ...scope, kind, quantile, ...window(minutes) } } })),
    refetchInterval: 15_000,
    placeholderData: (previous) => previous,
  })
}

export interface LogSearch {
  engineId: string
  group?: string
  /**
   * A config id; needs the group.
   */
  sessionId?: string
  minutes: number
  text?: string
  types: LogType[]
  msgTypes: string[]
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
              engineId: search.engineId,
              group: search.group,
              sessionId: search.sessionId,
              text: search.text || undefined,
              types: search.types,
              msgTypes: search.msgTypes,
              limit: search.limit,
              ...window(search.minutes),
            },
          },
        }),
      ),
    enabled,
  })
}

export interface DecodeTarget {
  ref: SessionRef
  message: string
  separator: string
}

export function useDecodedMessage(target: DecodeTarget | undefined) {
  return useQuery({
    queryKey: ['decode', target],
    queryFn: () =>
      call(api.POST('/api/messages/decode', { body: { ...target!.ref, message: target!.message, separator: target!.separator } })),
    enabled: target !== undefined,
    staleTime: Infinity,
    retry: false,
  })
}

/**
 * The message types the dictionaries of the sessions in scope define.
 */
export function useMessageTypes(scope: MetricScope) {
  return useQuery({
    queryKey: ['messageTypes', scope],
    queryFn: () => call(api.GET('/api/messages/types', { params: { query: scope } })),
    staleTime: 5 * 60_000,
  })
}
