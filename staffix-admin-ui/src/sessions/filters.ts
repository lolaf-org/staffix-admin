import { useSearchParams } from 'react-router'
import type { Schemas } from '../api/client'
import type { Status } from './status'
import { formatIdentity } from './identity'

/**
 * Kept in the URL, so a filtered view survives a reload and can be shared.
 */
export function useSessionFilters() {
  const [params, setParams] = useSearchParams()
  const text = params.get('q') ?? ''
  const statuses = params.getAll('status') as Status[]
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
    [session.group, `${session.group}.${session.id}`, session.instanceId, formatIdentity(session.identity), ...session.configIds]
      .join(' ')
      .toLowerCase()

  const matches = (session: Schemas['SessionSummary']) =>
    (statuses.length === 0 || statuses.includes(session.status)) &&
    (text === '' || searchable(session).includes(text.toLowerCase()))

  return {
    text,
    statuses,
    engines,
    setText: (value: string) => update('q', [value]),
    setStatuses: (values: Status[]) => update('status', values),
    setEngines: (values: string[]) => update('engine', values),
    matches,
  }
}
