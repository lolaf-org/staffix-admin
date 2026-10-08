import type { Schemas } from '../api/client'

/**
 * Worked out by the engine: a session an operator logged out is down on purpose, not failing.
 */
export type Status = Schemas['SessionSummary']['status']

export const STATUSES: Status[] = [
  'LOGGED_IN',
  'LOGGED_OUT_OUTSIDE_SESSION_TIME',
  'LOGGED_OUT_BY_OPERATOR',
  'LOGGED_OUT_INSIDE_SESSION_TIME',
]

export const STATUS_LABELS: Record<Status, string> = {
  LOGGED_IN: 'Logged in',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'Logged out, outside session time',
  LOGGED_OUT_BY_OPERATOR: 'Logged out by an operator',
  LOGGED_OUT_INSIDE_SESSION_TIME: 'Logged out, within session time',
}

/**
 * Ant Design semantic colours, and its orange preset between warning and error, so the boxes follow the theme.
 */
export const STATUS_COLORS: Record<Status, 'success' | 'warning' | 'orange' | 'error'> = {
  LOGGED_IN: 'success',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'warning',
  LOGGED_OUT_BY_OPERATOR: 'orange',
  LOGGED_OUT_INSIDE_SESSION_TIME: 'error',
}
