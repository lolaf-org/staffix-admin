import type { MetricScope } from '../api/queries'

export function metricsRoute(scope: MetricScope): string {
  const params = new URLSearchParams({ engine: scope.engineId })
  if (scope.group) params.set('group', scope.group)
  if (scope.sessionId) params.set('session', scope.sessionId)
  return `/metrics?${params}`
}
