import { useQuery } from '@tanstack/react-query'
import { api, call, isNotCollected, type Schemas } from '../api/client'
import { lastMinutes, type FixedPeriod, type TraceSettings } from '../api/queries'
import type { Period } from '../telemetry/TimeRangeSelector'

export const MAX_ROUNDS = 3
export const MAX_IDS = 20
export const LIMIT = 1000

export type TraceEntry = Schemas['LogEntry'] & {
  engineId: string
}

export interface Trace {
  /**
   * Oldest first.
   */
  entries: TraceEntry[]
  /**
   * The id asked first, then the ones found linked to it.
   */
  ids: string[]
  /**
   * Whether a limit stopped the trace: rounds, ids, or a search returning {@link LIMIT} entries.
   */
  truncated: boolean
  /**
   * Engines, or sessions of an engine, whose logs no connector collects.
   */
  notCollected: string[]
  failures: string[]
}

export function useTrace(id: string, engineIds: string[], period: Period, settings: TraceSettings | undefined) {
  return useQuery({
    queryKey: ['trace', id, engineIds, period, settings],
    queryFn: () => trace(id, engineIds, typeof period === 'number' ? lastMinutes(period) : period, settings!.linkingTags.map(String)),
    enabled: id !== '' && engineIds.length > 0 && settings !== undefined,
    refetchOnWindowFocus: false,
  })
}

/**
 * Searches the ids known so far on every engine, then the ids the messages found link to, round after round.
 */
async function trace(id: string, engineIds: string[], range: FixedPeriod, linkingTags: string[]): Promise<Trace> {
  const ids = new Set([id])
  const entries = new Map<string, TraceEntry>()
  const notCollected: string[] = []
  const failures: string[] = []
  let engines = engineIds
  let searching = [id]
  let truncated = false
  for (let round = 0; round < MAX_ROUNDS && searching.length > 0; round++) {
    const values = searching
    const results = await Promise.all(engines.map(async (engineId) => ({ engineId, result: await search(engineId, values, range) })))
    const found: TraceEntry[] = []
    results.forEach(({ engineId, result }) => {
      if (result.kind === 'found') {
        truncated ||= result.logs.entries.length === LIMIT
        if (round === 0) notCollected.push(...result.logs.notCollected.map((session) => `${engineId} ${session}`))
        found.push(...result.logs.entries.map((entry) => ({ ...entry, engineId })))
      } else if (result.kind === 'notCollected') {
        notCollected.push(engineId)
      } else {
        failures.push(`${engineId}: ${result.message}`)
      }
    })
    engines = results.filter(({ result }) => result.kind === 'found').map(({ engineId }) => engineId)
    searching = []
    for (const entry of found) {
      entries.set(`${entry.engineId}|${entry.group}|${entry.sessionId}|${entry.timestamp}|${entry.line}`, entry)
      const fields = fieldsOf(entry.line)
      for (const tag of linkingTags) {
        const value = fields.get(tag)
        if (value === undefined || ids.has(value)) continue
        if (ids.size === MAX_IDS) {
          truncated = true
        } else {
          ids.add(value)
          searching.push(value)
        }
      }
    }
  }
  return {
    entries: [...entries.values()].sort((a, b) => compareTimestamps(a.timestamp, b.timestamp)),
    ids: [...ids],
    truncated: truncated || searching.length > 0,
    notCollected,
    failures,
  }
}

type SearchResult =
  | { kind: 'found'; logs: Schemas['LogsResult'] }
  | { kind: 'notCollected' }
  | { kind: 'failed'; message: string }

async function search(engineId: string, fieldValues: string[], range: FixedPeriod): Promise<SearchResult> {
  try {
    const logs = await call(api.GET('/api/logs', { params: { query: { engineId, fieldValues, limit: LIMIT, ...range } } }))
    return { kind: 'found', logs }
  } catch (error) {
    if (isNotCollected(error)) return { kind: 'notCollected' }
    return { kind: 'failed', message: error instanceof Error ? error.message : String(error) }
  }
}

/**
 * The first value of each tag, fields separated by `|` or SOH.
 */
export function fieldsOf(line: string): Map<string, string> {
  const fields = new Map<string, string>()
  for (const field of line.split(/[|\u0001]/)) {
    const equals = field.indexOf('=')
    const tag = field.substring(0, equals)
    if (equals > 0 && !fields.has(tag)) fields.set(tag, field.substring(equals + 1))
  }
  return fields
}

/**
 * A value's description in the dictionary, each of several space separated values described, as the decoder does;
 * undefined for a value it does not describe.
 */
export function meaningOf(values: Record<string, string>, value: string): string | undefined {
  const meaning = value
    .split(' ')
    .map((one) => values[one] ?? one)
    .join(' ')
  return meaning === value ? undefined : meaning
}

export function separatorOf(line: string): string {
  return line.includes('\u0001') ? '\u0001' : '|'
}

/**
 * Down to the nanosecond the backends keep, which `Date` drops.
 */
function compareTimestamps(a: string, b: string): number {
  return Math.floor(Date.parse(a) / 1000) - Math.floor(Date.parse(b) / 1000) || nanosOf(a) - nanosOf(b)
}

function nanosOf(iso: string): number {
  const fraction = /\.(\d+)Z$/.exec(iso)
  return fraction?.[1] ? Number(fraction[1].padEnd(9, '0')) : 0
}
