import { useSearchParams } from 'react-router'
import type { Schemas } from '../api/client'
import type { Health } from './health'

/**
 * Kept in the URL, so a filtered view survives a reload and can be shared.
 */
export function useSessionFilters() {
  const [params, setParams] = useSearchParams()
  const text = params.get('q') ?? ''
  const healths = params.getAll('health') as Health[]
  const engines = params.getAll('engine')

  const update = (name: string, values: string[]) =>
    setParams(
      (current) => {
        current.delete(name)
        values.filter(Boolean).forEach((value) => current.append(name, value))
        return current
      },
      { replace: true },
    )

  const searchable = (session: Schemas['SessionSummary']) =>
    [session.group, `${session.group}.${session.id}`, session.instanceId, session.fixSessionId, ...session.configIds]
      .join(' ')
      .toLowerCase()

  const matches = (session: Schemas['SessionSummary']) =>
    (healths.length === 0 || healths.includes(session.health)) &&
    (text === '' || searchable(session).includes(text.toLowerCase()))

  return {
    text,
    healths,
    engines,
    setText: (value: string) => update('q', [value]),
    setHealths: (values: Health[]) => update('health', values),
    setEngines: (values: string[]) => update('engine', values),
    matches,
  }
}
