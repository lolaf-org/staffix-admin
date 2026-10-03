import { Alert } from 'antd'

/**
 * Sessions of the scope whose telemetry no connector collects, so a chart or a log list missing them is not
 * mistaken for an idle session.
 */
export function NotCollectedNotice({ what, sessions }: { what: 'metrics' | 'logs'; sessions: string[] }) {
  if (sessions.length === 0) return null
  return (
    <Alert
      type="info"
      showIcon
      title={`The ${what} of ${sessions.length === 1 ? 'one session are' : `${sessions.length} sessions are`} not collected`}
      description={`${sessions.join(', ')}: no connector is assigned to their ${what === 'metrics' ? 'monitoring plugin' : 'messages logger'}.`}
    />
  )
}
