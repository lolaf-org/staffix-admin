/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Alert, Drawer, Skeleton, Tabs } from 'antd'
import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { useSession, type SessionRef } from '../api/queries'
import { StatusBadge } from './StatusBadge'
import { SendMessageTab } from './SendMessageTab'
import { SessionSettingsTab } from './SessionSettingsTab'
import { SessionStatusTab } from './SessionStatusTab'
import { SessionTelemetryTab } from '../telemetry/SessionTelemetryTab'
import { SessionAlertsTab } from '../alerts/SessionAlertsTab'

export function SessionDrawer() {
  const params = useParams()
  const ref: SessionRef = { engineId: params.engineId ?? '', group: params.group ?? '', sessionId: params.sessionId ?? '' }
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession(ref)
  const [searchParams, setSearchParams] = useSearchParams()
  const [initialTab] = useState(searchParams.get('tab') ?? 'status')
  useEffect(() => {
    if (searchParams.has('tab')) {
      setSearchParams((current) => {
        current.delete('tab')
        return current
      }, { replace: true })
    }
  }, [searchParams, setSearchParams])
  const title = `${ref.group}.${ref.sessionId} on ${ref.engineId}`

  return (
    <Drawer
      open
      onClose={() => navigate({ pathname: '/', search: location.search })}
      size={950}
      title={session.data ? <StatusBadge status={session.data.summary.status} text={title} /> : title}
    >
      {session.isPending && <Skeleton active />}
      {session.isError && <Alert type="error" title={session.error.message} />}
      {session.isSuccess && (
        <Tabs
          destroyOnHidden
          defaultActiveKey={initialTab}
          items={[
            { key: 'status', label: 'Status', children: <SessionStatusTab sessionRef={ref} detail={session.data} /> },
            {
              key: 'settings',
              label: 'Settings',
              children: <SessionSettingsTab sessionRef={ref} detail={session.data} />,
            },
            {
              key: 'alerts',
              label: session.data.summary.alertCount > 0 ? `Alerts (${session.data.summary.alertCount})` : 'Alerts',
              children: <SessionAlertsTab sessionRef={ref} />,
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
