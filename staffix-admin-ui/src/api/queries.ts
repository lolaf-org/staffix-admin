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
 * The engine's own: its staffix version defines the settings it accepts. Static for the engine's life.
 */
export function useSessionSettingsSchema(engineId: string) {
  return useQuery({
    queryKey: ['sessionSettingsSchema', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/schemas/session-settings', { params: { path: { engineId } } })),
    staleTime: Infinity,
  })
}

export type SessionSettingsStore = Schemas['SessionSettingsStore']
export type ApplicationSetting = Schemas['ApplicationSetting']

export function useSessionSettingsStores(engineId: string) {
  return useQuery({
    queryKey: ['sessionSettingsStores', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/session-settings-stores', { params: { path: { engineId } } })),
    staleTime: Infinity,
  })
}

/**
 * Those the session's application declares, with their descriptions and which are secret.
 */
export function useApplicationSettings(ref: SessionRef | undefined) {
  return useQuery({
    queryKey: ['applicationSettings', ref?.engineId, ref?.group, ref?.sessionId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/application-settings', sessionPath(ref!))),
    enabled: ref !== undefined,
  })
}

export function fetchSessionSettings(ref: SessionRef) {
  return call(api.GET('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', sessionPath(ref)))
}

/**
 * Writes a session's settings, refreshing them and the engine's sessions once the engine took them.
 */
function useSettingsMutation<V>(engineId: string, mutationFn: (variables: V) => Promise<unknown>, success: string) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      message.success(success)
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups', engineId] }),
        queryClient.invalidateQueries({ queryKey: ['settings', engineId] }),
      ])
    },
  })
}

export function useUpdateSessionSettings(ref: SessionRef) {
  return useSettingsMutation(ref.engineId, (settings: Record<string, unknown>) =>
    call(api.PUT('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', { ...sessionPath(ref), body: settings })),
  'Settings saved; the session restarts on them')
}

export function useRemoveSession(ref: SessionRef) {
  return useSettingsMutation(ref.engineId, () =>
    call(api.DELETE('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', sessionPath(ref))),
  'Session removed')
}

export function useAddSession(engineId: string) {
  return useSettingsMutation(engineId, ({ store, settings }: { store: string; settings: Record<string, unknown> }) =>
    call(api.POST('/api/engines/{engineId}/session-settings-stores/{sessionSettingsStore}/sessions', {
      params: { path: { engineId, sessionSettingsStore: store } },
      body: settings,
    })),
  'Session added')
}

/**
 * A state-changing call on an engine: reports the outcome to the operator and refreshes that engine's sessions.
 */
export function useEngineMutation<V>(engineId: string, mutationFn: (variables: V) => Promise<unknown>,
                                     success: string | ((variables: V) => string)) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn,
    onSuccess: (_, variables) => {
      message.success(typeof success === 'string' ? success : success(variables))
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

/**
 * A period in the past, so not refreshed.
 */
export interface FixedPeriod {
  from: string
  to: string
}

/**
 * `period` is the last minutes, refreshed, or a fixed period; `rateWindow` an ISO duration the console may widen,
 * the step when unset.
 */
export function useMetrics(scope: MetricScope, kind: MetricKind, quantile: number, period: number | FixedPeriod, rateWindow?: string) {
  const live = typeof period === 'number'
  return useQuery({
    queryKey: ['metrics', scope, kind, quantile, period, rateWindow],
    queryFn: () =>
      call(api.GET('/api/metrics', { params: { query: { ...scope, kind, quantile, rateWindow, ...(live ? window(period) : period) } } })),
    refetchInterval: live ? 15_000 : false,
    placeholderData: (previous) => previous,
  })
}

/**
 * The kinds the monitoring plugins of the scope's sessions measure.
 */
export function useMetricKinds(scope: MetricScope) {
  return useQuery({
    queryKey: ['metricKinds', scope],
    queryFn: () => call(api.GET('/api/metrics/kinds', { params: { query: scope } })),
    staleTime: 60_000,
  })
}

export interface LogSearch {
  engineId: string
  group?: string
  /**
   * A config id; needs the group.
   */
  sessionId?: string
  /**
   * The last minutes up to now, or a fixed period.
   */
  period: number | FixedPeriod
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
              ...(typeof search.period === 'number' ? window(search.period) : { from: search.period.from, to: search.period.to }),
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
  /**
   * When the message was logged, to show the session's metrics around it; unset for a message not sent yet.
   */
  timestamp?: string
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

export type ConnectorsConfig = Schemas['ConnectorsConfig']
export type Connector = Schemas['Connector']
export type ConnectorKind = Schemas['ConnectorKind']

export function useConnectors() {
  return useQuery({
    queryKey: ['connectors'],
    queryFn: () => call(api.GET('/api/connectors')),
  })
}

export function useConnectorKinds() {
  return useQuery({
    queryKey: ['connectors', 'kinds'],
    queryFn: () => call(api.GET('/api/connectors/kinds')),
    staleTime: Infinity,
  })
}

export function useConnectorEngines() {
  return useQuery({
    queryKey: ['connectors', 'engines'],
    queryFn: () => call(api.GET('/api/connectors/engines')),
    refetchInterval: 10_000,
  })
}

export function useSaveConnectors() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (config: ConnectorsConfig) => call(api.PUT('/api/connectors', { body: config })),
    onSuccess: (saved) => queryClient.setQueryData(['connectors'], saved),
  })
}

export function useTestConnector() {
  return useMutation({
    mutationFn: (connector: Connector) => call(api.POST('/api/connectors/test', { body: connector })),
  })
}
