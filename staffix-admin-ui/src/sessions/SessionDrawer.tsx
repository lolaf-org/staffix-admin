import { Alert, Drawer, Skeleton, Tabs } from 'antd'
import { useLocation, useNavigate, useParams } from 'react-router'
import { useSession, type SessionRef } from '../api/queries'
import { statusOf } from './health'
import { StatusBadge } from './StatusBadge'
import { SendMessageTab } from './SendMessageTab'
import { SessionSettingsTab } from './SessionSettingsTab'
import { SessionStatusTab } from './SessionStatusTab'
import { SessionTelemetryTab } from '../telemetry/SessionTelemetryTab'

export function SessionDrawer() {
  const params = useParams()
  const ref: SessionRef = { engineId: params.engineId ?? '', group: params.group ?? '', sessionId: params.sessionId ?? '' }
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession(ref)
  const title = `${ref.group}.${ref.sessionId} on ${ref.engineId}`

  return (
    <Drawer
      open
      onClose={() => navigate({ pathname: '/', search: location.search })}
      size={760}
      title={session.data ? <StatusBadge status={statusOf(session.data.summary)} text={title} /> : title}
    >
      {session.isPending && <Skeleton active />}
      {session.isError && <Alert type="error" title={session.error.message} />}
      {session.isSuccess && (
        <Tabs
          destroyOnHidden
          items={[
            { key: 'status', label: 'Status', children: <SessionStatusTab sessionRef={ref} detail={session.data} /> },
            {
              key: 'settings',
              label: 'Settings',
              children: <SessionSettingsTab sessionRef={ref} runningConfigId={session.data.summary.runningConfigId} />,
            },
            { key: 'metrics', label: 'Metrics', children: <SessionTelemetryTab sessionRef={ref} summary={session.data.summary} view="metrics" /> },
            { key: 'logs', label: 'Logs', children: <SessionTelemetryTab sessionRef={ref} summary={session.data.summary} view="logs" /> },
            { key: 'send', label: 'Send message', children: <SendMessageTab sessionRef={ref} summary={session.data.summary} /> },
          ]}
        />
      )}
    </Drawer>
  )
}
