/**
 * An ISO-8601 duration as the engine writes it (`PT10S`, `PT1H30M`, `P2D`), as an amount in the
 * largest unit it is a whole number of.
 */
export const DURATION_UNITS = [
  { value: 'd', label: 'days', seconds: 86400 },
  { value: 'h', label: 'hours', seconds: 3600 },
  { value: 'min', label: 'minutes', seconds: 60 },
  { value: 's', label: 'seconds', seconds: 1 },
  { value: 'ms', label: 'milliseconds', seconds: 0.001 },
] as const

export type DurationUnit = (typeof DURATION_UNITS)[number]['value']

const ISO = /^(-)?P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?$/

export function parseDuration(iso: string): { amount: number; unit: DurationUnit } | undefined {
  const match = ISO.exec(iso)
  if (!match) return undefined
  const [, negative, days, hours, minutes, seconds] = match
  const total = (Number(days ?? 0) * 86400 + Number(hours ?? 0) * 3600 + Number(minutes ?? 0) * 60 + Number(seconds ?? 0)) * (negative ? -1 : 1)
  const unit = DURATION_UNITS.find((u) => isWhole(total / u.seconds)) ?? DURATION_UNITS[DURATION_UNITS.length - 1]!
  return { amount: round(total / unit.seconds), unit: unit.value }
}

export function durationSeconds(iso: string): number | undefined {
  const duration = parseDuration(iso)
  return duration && round(duration.amount * DURATION_UNITS.find((u) => u.value === duration.unit)!.seconds)
}

export function formatDuration(amount: number, unit: DurationUnit): string {
  const seconds = round(amount * DURATION_UNITS.find((u) => u.value === unit)!.seconds)
  return `PT${seconds}S`
}

function isWhole(value: number): boolean {
  return Math.abs(value - Math.round(value)) < 1e-9
}

/**
 * Nine decimals, a nanosecond, which is what the engine's durations resolve to.
 */
function round(value: number): number {
  return Number(value.toFixed(9))
}
