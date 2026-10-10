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
import { Alert, Empty, Flex, Skeleton, Table, Typography } from 'antd'
import { useNavigate } from 'react-router'
import type { Schemas } from '../api/client'
import { sessionRoute, useAlerts } from '../api/queries'
import { formatDateTime, formatDuration } from '../telemetry/format'
import { AlertLevelTag } from './AlertLevelTag'

type ActiveAlert = Schemas['ActiveAlert']

/**
 * Every engine's ongoing alerts in one table, as the console receives them: the worst first, then the oldest.
 */
export function AlertsPage() {
  const alerts = useAlerts()
  const navigate = useNavigate()

  if (alerts.isPending) return <Skeleton active />
  if (alerts.isError) return <Alert type="error" title={alerts.error.message} />

  const now = Date.now()
  return (
    <Flex vertical gap={16}>
      {alerts.data.unavailableEngineIds.length > 0 && (
        <Alert
          type="warning"
          showIcon
          title={`Alerts unknown for ${alerts.data.unavailableEngineIds.join(', ')}: the engine does not answer`}
        />
      )}
      <Table<ActiveAlert>
        size="small"
        rowKey={(alert) => `${alert.engineId}/${alert.group}/${alert.sessionId}/${alert.probe}`}
        dataSource={alerts.data.alerts}
        pagination={false}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No ongoing alert" /> }}
        onRow={(alert) => ({
          onClick: () => navigate({
            pathname: sessionRoute({ engineId: alert.engineId, group: alert.group, sessionId: alert.sessionId }),
            search: '?tab=alerts',
          }),
          style: { cursor: 'pointer' },
        })}
        columns={[
          { title: 'Level', dataIndex: 'level', width: 100, render: (level: ActiveAlert['level']) => <AlertLevelTag level={level} /> },
          { title: 'Engine', dataIndex: 'engineId' },
          {
            title: 'Session',
            render: (_, alert) => (
              <Typography.Text style={{ whiteSpace: 'nowrap' }}>
                {alert.group}.{alert.sessionId}
                {alert.configId !== alert.sessionId && <Typography.Text type="secondary"> on {alert.configId}</Typography.Text>}
              </Typography.Text>
            ),
          },
          { title: 'Probe', dataIndex: 'probe', render: (probe: string) => <Typography.Text code>{probe}</Typography.Text> },
          { title: 'Message', dataIndex: 'message' },
          {
            title: 'Since',
            dataIndex: 'since',
            render: (since: string) => (
              <Typography.Text style={{ whiteSpace: 'nowrap' }}>
                {formatDateTime(Date.parse(since))}
                <Typography.Text type="secondary"> for {formatDuration(now - Date.parse(since))}</Typography.Text>
              </Typography.Text>
            ),
          },
        ]}
      />
    </Flex>
  )
}
