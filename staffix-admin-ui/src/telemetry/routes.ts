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
import type { MetricScope, SessionRef } from '../api/queries'

export function metricsRoute(scope: MetricScope): string {
  const params = new URLSearchParams({ engine: scope.engineId })
  if (scope.group) params.set('group', scope.group)
  if (scope.sessionId) params.set('session', scope.sessionId)
  return `/metrics?${params}`
}

export function traceRoute(id: string, engineId?: string): string {
  const params = new URLSearchParams({ id })
  if (engineId) params.set('engine', engineId)
  return `/trace?${params}`
}

export function sessionRoute(ref: SessionRef): string {
  return `/engines/${encodeURIComponent(ref.engineId)}/groups/${encodeURIComponent(ref.group)}/sessions/${encodeURIComponent(ref.sessionId)}`
}
