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

export function enginePath(engineId: string) {
  return { params: { path: { engineId } } }
}

export function sessionRoute(ref: SessionRef): string {
  return `/engines/${encodeURIComponent(ref.engineId)}/groups/${encodeURIComponent(ref.group)}/sessions/${encodeURIComponent(ref.sessionId)}`
}

export function useGroups(engineId: string | undefined) {
  return useQuery({
    queryKey: ['groups', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/groups', enginePath(engineId!))),
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
    queryFn: () => call(api.GET('/api/engines/{engineId}/schemas/session-settings', enginePath(engineId))),
    staleTime: Infinity,
  })
}

export type SessionSettingsStore = Schemas['SessionSettingsStore']
export type ApplicationSetting = Schemas['ApplicationSetting']

export function useSessionSettingsStores(engineId: string) {
  return useQuery({
    queryKey: ['sessionSettingsStores', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/session-settings-stores', enginePath(engineId))),
    staleTime: Infinity,
  })
}

export type SessionComponents = Schemas['SessionComponents']

/**
 * What a session's settings can name in its engine; an engine not serving it leaves those fields as free text.
 */
export function useSessionComponents(engineId: string) {
  return useQuery({
    queryKey: ['sessionComponents', engineId],
    queryFn: () => call(api.GET('/api/engines/{engineId}/session-components', enginePath(engineId))),
    staleTime: Infinity,
    retry: false,
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

export function useUpdateSessionSettings(ref: SessionRef) {
  return useEngineMutation(ref.engineId, (settings: Record<string, unknown>) =>
    call(api.PUT('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', { ...sessionPath(ref), body: settings })),
  'Settings saved; the session restarts on them', SETTINGS_WRITE)
}

export function useRemoveSession(ref: SessionRef) {
  return useEngineMutation(ref.engineId, () =>
    call(api.DELETE('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/settings', sessionPath(ref))),
  'Session removed', SETTINGS_WRITE)
}

export function useAddSession(engineId: string) {
  return useEngineMutation(engineId, ({ store, settings }: { store: string; settings: Record<string, unknown> }) =>
    call(api.POST('/api/engines/{engineId}/session-settings-stores/{sessionSettingsStore}/sessions', {
      params: { path: { engineId, sessionSettingsStore: store } },
      body: settings,
    })),
  'Session added', SETTINGS_WRITE)
}

const SESSION = '/api/engines/{engineId}/groups/{group}/sessions/{sessionId}'

export function useLogon(ref: SessionRef) {
  return useEngineMutation(ref.engineId, () => call(api.POST(`${SESSION}/logon`, sessionPath(ref))), 'Logon requested')
}

export function useLogout(ref: SessionRef) {
  return useEngineMutation(ref.engineId, () => call(api.POST(`${SESSION}/logout`, sessionPath(ref))), 'Logout requested')
}

export function useResetSession(ref: SessionRef) {
  return useEngineMutation(ref.engineId, (body: Schemas['ResetRequest']) =>
    call(api.POST(`${SESSION}/reset`, { ...sessionPath(ref), body })), 'Reset requested')
}

export function useSetSeqNums(ref: SessionRef) {
  return useEngineMutation(ref.engineId, (body: Schemas['SeqNumsRequest']) =>
    call(api.PUT(`${SESSION}/seqnums`, { ...sessionPath(ref), body })), 'Sequence numbers updated')
}

export function useActivateConfig(ref: SessionRef) {
  return useEngineMutation(ref.engineId, (configId: string) =>
    call(api.POST(`${SESSION}/activate`, { ...sessionPath(ref), body: { configId } })), 'Switch requested')
}

export function useSendMessages(ref: SessionRef) {
  return useEngineMutation(ref.engineId, (body: Schemas['SendMessageRequest']) =>
    call(api.POST(`${SESSION}/messages`, { ...sessionPath(ref), body })),
  (body) => (body.messages.length === 1 ? 'Message sent' : `${body.messages.length} messages sent`))
}

interface EngineMutationOptions {
  /**
   * Also refreshes the engine's session settings, for a call that wrote some.
   */
  refreshSettings?: boolean
  /**
   * Off for a form that shows the failure itself.
   */
  reportErrors?: boolean
}

/**
 * A session settings write, whose form shows its own failure.
 */
const SETTINGS_WRITE: EngineMutationOptions = { refreshSettings: true, reportErrors: false }

/**
 * A state-changing call on an engine: reports the outcome to the operator and refreshes that engine's sessions.
 */
export function useEngineMutation<V>(engineId: string, mutationFn: (variables: V) => Promise<unknown>,
                                     success: string | ((variables: V) => string),
                                     { refreshSettings = false, reportErrors = true }: EngineMutationOptions = {}) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  return useMutation({
    mutationFn,
    onSuccess: (_, variables) => {
      message.success(typeof success === 'string' ? success : success(variables))
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['groups', engineId] }),
        ...(refreshSettings ? [queryClient.invalidateQueries({ queryKey: ['settings', engineId] })] : []),
      ])
    },
    onError: reportErrors ? (error) => message.error(error.message) : undefined,
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

export type Meter = Schemas['Meter']
export type MetricStatistic = paths['/api/metrics']['get']['parameters']['query']['statistic']
export type LogType = NonNullable<Schemas['LogEntry']['type']>

/**
 * The last minutes up to now.
 */
export function lastMinutes(minutes: number): FixedPeriod {
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
 * What a chart reads: the message rate, or one meter's statistic split by its tags.
 */
export type MetricRequest =
  | { kind: 'messages' }
  | { kind: 'meter'; meter: string; statistic: MetricStatistic; quantile?: number; splitBy: string[]; gaugeUnit?: Meter['unit'] }

/**
 * `period` is the last minutes, refreshed, or a fixed period; `rateWindow` an ISO duration the console may widen,
 * the step when unset. `live` refreshes the last minutes every couple of seconds rather than every 15.
 */
export function useMetrics(scope: MetricScope, request: MetricRequest | undefined, period: number | FixedPeriod, rateWindow?: string, live = false) {
  const relative = typeof period === 'number'
  return useQuery({
    queryKey: ['metrics', scope, request, period, rateWindow],
    queryFn: () => {
      const range = relative ? lastMinutes(period) : period
      return request!.kind === 'messages'
        ? call(api.GET('/api/metrics/messages', { params: { query: { ...scope, rateWindow, ...range } } }))
        : call(
            api.GET('/api/metrics', {
              params: {
                query: {
                  ...scope,
                  meter: request!.meter,
                  statistic: request!.statistic,
                  quantile: request!.quantile,
                  splitBy: request!.splitBy,
                  gaugeUnit: request!.gaugeUnit ?? undefined,
                  rateWindow,
                  ...range,
                },
              },
            }),
          )
    },
    enabled: request !== undefined,
    refetchInterval: relative ? (live ? POLL_INTERVAL_MS : 15_000) : false,
    placeholderData: (previous) => previous,
  })
}

/**
 * The meters the scope's running sessions publish, as their engine describes them.
 */
export function useMeters(scope: MetricScope) {
  return useQuery({
    queryKey: ['meters', scope],
    queryFn: () => call(api.GET('/api/metrics/meters', { params: { query: scope } })),
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

/**
 * `live` re-reads the last minutes every couple of seconds, keeping the shown entries while it does.
 */
export function useLogs(search: LogSearch, enabled: boolean, live = false) {
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
              ...(typeof search.period === 'number' ? lastMinutes(search.period) : { from: search.period.from, to: search.period.to }),
            },
          },
        }),
      ),
    enabled,
    refetchInterval: live ? POLL_INTERVAL_MS : false,
    placeholderData: live ? (previous) => previous : undefined,
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
  return useQuery(messageTypesQuery(scope))
}

export type TraceSettings = Schemas['TraceSettings']

/**
 * How the order trace follows ids and what it shows: the console's configuration, else its defaults.
 */
export function useTraceSettings() {
  return useQuery({
    queryKey: ['traceSettings'],
    queryFn: () => call(api.GET('/api/trace/settings')),
    staleTime: 5 * 60_000,
  })
}

export type FieldInfo = Schemas['FieldInfo']

/**
 * The fields of `tags` in the session's dictionaries, with their enum values: a session's own, as dictionaries of
 * different FIX versions give a value different meanings.
 */
export function sessionFieldsQuery(ref: SessionRef, tags: number[]) {
  return {
    queryKey: ['fields', ref, tags],
    queryFn: () => call(api.GET('/api/messages/fields', { params: { query: { ...ref, tags } } })),
    staleTime: 5 * 60_000,
  }
}

export function messageTypesQuery(scope: MetricScope) {
  return {
    queryKey: ['messageTypes', scope],
    queryFn: () => call(api.GET('/api/messages/types', { params: { query: scope } })),
    staleTime: 5 * 60_000,
  }
}

export type ConsoleConfig = Schemas['ConsoleConfig']
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
    mutationFn: (config: ConsoleConfig) => call(api.PUT('/api/connectors', { body: config })),
    onSuccess: (saved) => queryClient.setQueryData(['connectors'], saved),
  })
}

export function useTestConnector() {
  return useMutation({
    mutationFn: (connector: Connector) => call(api.POST('/api/connectors/test', { body: connector })),
  })
}
