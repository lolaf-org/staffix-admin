import type { Schemas } from '../api/client'

export type Health = Schemas['SessionSummary']['health']

/**
 * The health, except that a session an operator logged out is told apart: it is down on purpose, not failing.
 */
export type Status = Health | 'LOGGED_OUT_BY_OPERATOR'

export const STATUSES: Status[] = [
  'LOGGED_IN',
  'LOGGED_OUT_OUTSIDE_SESSION_TIME',
  'LOGGED_OUT_BY_OPERATOR',
  'LOGGED_OUT_WITHIN_SESSION_TIME',
]

export const STATUS_LABELS: Record<Status, string> = {
  LOGGED_IN: 'Logged in',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'Logged out, outside session time',
  LOGGED_OUT_BY_OPERATOR: 'Logged out by an operator',
  LOGGED_OUT_WITHIN_SESSION_TIME: 'Logged out, within session time',
}

export const HEALTH_LABELS: Record<Health, string> = {
  LOGGED_IN: STATUS_LABELS.LOGGED_IN,
  LOGGED_OUT_OUTSIDE_SESSION_TIME: STATUS_LABELS.LOGGED_OUT_OUTSIDE_SESSION_TIME,
  LOGGED_OUT_WITHIN_SESSION_TIME: STATUS_LABELS.LOGGED_OUT_WITHIN_SESSION_TIME,
}

/**
 * Ant Design semantic colours, and its orange preset between warning and error, so the boxes follow the theme.
 */
export const STATUS_COLORS: Record<Status, 'success' | 'warning' | 'orange' | 'error'> = {
  LOGGED_IN: 'success',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'warning',
  LOGGED_OUT_BY_OPERATOR: 'orange',
  LOGGED_OUT_WITHIN_SESSION_TIME: 'error',
}

export function statusOf(session: Schemas['SessionSummary']): Status {
  return session.health !== 'LOGGED_IN' && session.desiredState === 'LOGGED_OUT' ? 'LOGGED_OUT_BY_OPERATOR' : session.health
}
