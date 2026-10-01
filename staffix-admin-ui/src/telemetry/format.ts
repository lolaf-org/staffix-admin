export function formatSeconds(seconds: number): string {
  const abs = Math.abs(seconds)
  if (abs === 0) return '0'
  if (abs < 1e-3) return `${Number((seconds * 1e6).toFixed(1))} µs`
  if (abs < 1) return `${(seconds * 1e3).toFixed(abs < 1e-2 ? 2 : 1)} ms`
  return `${seconds.toFixed(2)} s`
}

/**
 * The unit is in the chart title, so axis ticks stay short.
 */
export function formatRateValue(perSecond: number): string {
  return String(Number(perSecond < 1 ? perSecond.toFixed(2) : perSecond.toFixed(1)))
}

export function formatClock(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export function formatDateTime(epochMillis: number): string {
  return new Date(epochMillis).toLocaleString([], { dateStyle: 'short', timeStyle: 'medium' })
}

export function formatLogTime(iso: string): string {
  const date = new Date(iso)
  return `${date.toLocaleTimeString([], { hour12: false })}.${String(date.getMilliseconds()).padStart(3, '0')}`
}
