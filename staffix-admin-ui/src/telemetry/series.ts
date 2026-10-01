/**
 * First three slots of the validated reference palette: distinct for every pair, colour-blind safe included.
 * Dark mode has its own steps, validated against the dark surface. A fourth series would be ambiguous, so charts
 * plot at most three.
 */
const LIGHT_SERIES_COLORS = ['#2a78d6', '#eb6834', '#1baf7a']
const DARK_SERIES_COLORS = ['#3987e5', '#d95926', '#199e70']

export const SERIES_COUNT = LIGHT_SERIES_COLORS.length

export function seriesColors(dark: boolean): string[] {
  return dark ? DARK_SERIES_COLORS : LIGHT_SERIES_COLORS
}

const MESSAGE_TYPE_NAMES: Record<string, string> = {
  D: 'NewOrderSingle',
  '8': 'ExecutionReport',
  '0': 'Heartbeat',
  F: 'OrderCancelRequest',
  G: 'OrderCancelReplaceRequest',
  '9': 'OrderCancelReject',
  A: 'Logon',
  '5': 'Logout',
  '1': 'TestRequest',
  '2': 'ResendRequest',
  '4': 'SequenceReset',
  '3': 'Reject',
}

/**
 * Application flow before session traffic, so the three plotted types are the ones operators look at first.
 */
const PRIORITY = ['D', '8', 'F', 'G', '9', '0', 'A', '5', '1', '2', '4', '3']

export function messageTypeName(code: string): string {
  return MESSAGE_TYPE_NAMES[code] ? `${MESSAGE_TYPE_NAMES[code]} (${code})` : `MsgType ${code}`
}

export function plottedMessageTypes(codes: string[]): string[] {
  const rank = (code: string) => (PRIORITY.includes(code) ? PRIORITY.indexOf(code) : PRIORITY.length)
  return [...new Set(codes)].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b)).slice(0, SERIES_COUNT)
}
