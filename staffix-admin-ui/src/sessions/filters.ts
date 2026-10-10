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
import { useSearchParams } from 'react-router'
import type { Schemas } from '../api/client'
import type { Status } from './status'
import type { AlertLevel } from '../alerts/levels'
import { formatIdentity } from './identity'

/**
 * Kept in the URL, so a filtered view survives a reload and can be shared.
 */
export function useSessionFilters() {
  const [params, setParams] = useSearchParams()
  const text = params.get('q') ?? ''
  const statuses = params.getAll('status') as Status[]
  const engines = params.getAll('engine')
  const alertLevels = params.getAll('alert') as AlertLevel[]

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
    (alertLevels.length === 0 || (session.alertLevel != null && alertLevels.includes(session.alertLevel))) &&
    (text === '' || searchable(session).includes(text.toLowerCase()))

  return {
    text,
    statuses,
    engines,
    alertLevels,
    setText: (value: string) => update('q', [value]),
    setStatuses: (values: Status[]) => update('status', values),
    setEngines: (values: string[]) => update('engine', values),
    /**
     * Matches a session by its worst ongoing level.
     */
    setAlertLevels: (values: AlertLevel[]) => update('alert', values),
    matches,
  }
}
