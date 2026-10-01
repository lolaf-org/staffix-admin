/**
 * First three slots of the validated reference palette: distinct for every pair, colour-blind safe included.
 * A fourth series would be ambiguous, so charts plot at most three.
 */
export const SERIES_COLORS = ['#2a78d6', '#eb6834', '#1baf7a']

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
  return [...new Set(codes)].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b)).slice(0, SERIES_COLORS.length)
}
