import type { Schemas } from '../api/client'
import type { ConnectorKind, ConsoleConfig } from '../api/queries'

export type Role = ConnectorKind['role']
export type EngineMapping = Schemas['EngineMapping']
export type RoleConnectors = Schemas['EngineConnectors']

/**
 * Assigned to say this telemetry is not collected.
 */
export const NONE = 'none'
/**
 * What the backend shows of a stored secret that is not a reference; sent back, it keeps the stored value.
 */
export const SET = 'set'

export const ROLE_LABELS: Record<Role, string> = { METRICS: 'Metrics', LOGS: 'Logs' }

export function roleKey(role: Role): keyof RoleConnectors {
  return role === 'METRICS' ? 'metrics' : 'logs'
}

export function roleOf(kinds: ConnectorKind[], kind: string): Role | undefined {
  return kinds.find((k) => k.kind === kind)?.role
}

export function connectorName(config: ConsoleConfig, id: string | null | undefined): string {
  if (!id || id === NONE) return 'not collected'
  return config.connectors.find((c) => c.id === id)?.name ?? id
}

/**
 * Options to assign a connector of a role, the empty value being unset: it falls back on {@code inherited}, or
 * collects nothing at the top level.
 */
export function assignmentOptions(config: ConsoleConfig, kinds: ConnectorKind[], role: Role, inherited?: string) {
  const connectors = config.connectors
    .filter((c) => roleOf(kinds, c.kind) === role)
    .map((c) => ({ value: c.id, label: c.name }))
  if (inherited === undefined) {
    return [{ value: '', label: 'Not collected' }, ...connectors]
  }
  return [{ value: '', label: `Inherited: ${inherited}` }, ...connectors, { value: NONE, label: 'Not collected' }]
}

export function engineMapping(config: ConsoleConfig, engineId: string): EngineMapping {
  return config.engines[engineId] ?? { defaults: {}, loggers: {}, monitors: {} }
}

/**
 * An engine entry left without any assignment is dropped, so the file only names engines that differ.
 */
export function withEngineMapping(config: ConsoleConfig, engineId: string, mapping: EngineMapping): ConsoleConfig {
  const engines = { ...config.engines }
  const empty = !mapping.defaults.metrics && !mapping.defaults.logs
    && Object.keys(mapping.loggers).length === 0 && Object.keys(mapping.monitors).length === 0
  if (empty) {
    delete engines[engineId]
  } else {
    engines[engineId] = mapping
  }
  return { ...config, engines }
}

export function engineDefault(config: ConsoleConfig, engineId: string, role: Role): string {
  const key = roleKey(role)
  const own = config.engines[engineId]?.defaults[key]
  return own ? `${connectorName(config, own)} (engine default)` : `${connectorName(config, config.defaults[key])} (global default)`
}
