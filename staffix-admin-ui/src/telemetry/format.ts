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

export function formatClockSeconds(epochMillis: number): string {
  return new Date(epochMillis).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function formatDateTime(epochMillis: number): string {
  return new Date(epochMillis).toLocaleString([], { dateStyle: 'short', timeStyle: 'medium' })
}

/**
 * The finest precision the entries carry, at least milliseconds: an ISO instant drops trailing zero groups, so one
 * microsecond timestamp falling on a whole millisecond would otherwise print three digits.
 */
export function logTimeDigits(timestamps: string[]): number {
  return timestamps.reduce((digits, timestamp) => Math.max(digits, fractionOf(timestamp).length), 3)
}

/**
 * The time to the millisecond, then the microsecond and nanosecond groups the precision calls for. The fraction is
 * read from the text: a `Date` keeps only milliseconds.
 */
export function logTimeParts(iso: string, digits: number): { millis: string; finer: string[] } {
  const fraction = fractionOf(iso).padEnd(digits, '0')
  return {
    millis: `${new Date(iso).toLocaleTimeString([], { hour12: false })}.${fraction.slice(0, 3)}`,
    finer: fraction.slice(3, digits).match(/\d{3}/g) ?? [],
  }
}

export function logTimeWidth(digits: number): number {
  return 110 + 9 * (digits - 3)
}

function fractionOf(iso: string): string {
  return /\.(\d+)/.exec(iso)?.[1] ?? ''
}

export function separatorOf(line: string): string {
  return line.includes('\u0001') ? '\u0001' : '|'
}

export function formatDuration(millis: number): string {
  const seconds = Math.round(millis / 1000)
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 48) return minutes % 60 === 0 ? `${hours} h` : `${hours} h ${minutes % 60} min`
  return `${Math.floor(hours / 24)} d ${hours % 24} h`
}
