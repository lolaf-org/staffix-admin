export const TIME_WINDOWS = [
  { value: 15, label: '15m' },
  { value: 60, label: '1h' },
  { value: 360, label: '6h' },
  { value: 1440, label: '24h' },
  { value: 2880, label: '2d' },
  { value: 10080, label: '7d' },
] as const

/**
 * The window a live view follows: the shortest.
 */
export const LIVE_WINDOW = TIME_WINDOWS[0].value
