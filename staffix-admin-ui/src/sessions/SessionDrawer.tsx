import { Alert, Badge, Drawer, Skeleton, Tabs } from 'antd'
import { useLocation, useNavigate, useParams } from 'react-router'
import { useSession } from '../api/queries'
import { HEALTH_STATUS } from './health'
import { SendMessageTab } from './SendMessageTab'
import { SessionSettingsTab } from './SessionSettingsTab'
import { SessionStatusTab } from './SessionStatusTab'
import { LogsPanel } from '../telemetry/LogsPanel'
import { MetricsTab } from '../telemetry/MetricsTab'

export function SessionDrawer() {
  const { instanceId = '', sessionId = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession(instanceId, sessionId)

  const close = () => navigate({ pathname: '/', search: location.search })

  return (
    <Drawer
      open
      onClose={close}
      size={760}
      title={
        session.data ? (
          <Badge status={HEALTH_STATUS[session.data.summary.health]} text={`${sessionId} on ${instanceId}`} />
        ) : (
          `${sessionId} on ${instanceId}`
        )
      }
    >
      {session.isPending && <Skeleton active />}
      {session.isError && <Alert type="error" title={session.error.message} />}
      {session.isSuccess && (
        <Tabs
          destroyOnHidden
          items={[
            { key: 'status', label: 'Status', children: <SessionStatusTab instanceId={instanceId} detail={session.data} /> },
            { key: 'settings', label: 'Settings', children: <SessionSettingsTab instanceId={instanceId} sessionId={sessionId} /> },
            { key: 'metrics', label: 'Metrics', children: <MetricsTab instanceId={instanceId} sessionId={sessionId} /> },
            { key: 'logs', label: 'Logs', children: <LogsPanel instanceId={instanceId} sessionId={sessionId} /> },
            { key: 'send', label: 'Send message', children: <SendMessageTab instanceId={instanceId} summary={session.data.summary} /> },
          ]}
        />
      )}
    </Drawer>
  )
}
