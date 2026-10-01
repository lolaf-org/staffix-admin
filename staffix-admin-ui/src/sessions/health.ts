import type { Schemas } from '../api/client'

export type Health = Schemas['SessionSummary']['health']

export const HEALTHS: Health[] = ['LOGGED_IN', 'LOGGED_OUT_OUTSIDE_SESSION_TIME', 'LOGGED_OUT_WITHIN_SESSION_TIME']

export const HEALTH_LABELS: Record<Health, string> = {
  LOGGED_IN: 'Logged in',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'Logged out, outside session time',
  LOGGED_OUT_WITHIN_SESSION_TIME: 'Logged out, within session time',
}

/**
 * Ant Design semantic colours, so the boxes follow the theme.
 */
export const HEALTH_STATUS: Record<Health, 'success' | 'warning' | 'error'> = {
  LOGGED_IN: 'success',
  LOGGED_OUT_OUTSIDE_SESSION_TIME: 'warning',
  LOGGED_OUT_WITHIN_SESSION_TIME: 'error',
}
