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
import { useState } from 'react'
import { Button, Flex, Segmented, Typography } from 'antd'
import { ExpandAltOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router'
import type { Schemas } from '../api/client'
import type { SessionRef } from '../api/queries'
import { LogsPanel } from './LogsPanel'
import { MetricsPanel } from './MetricsPanel'
import { metricsRoute } from './routes'

interface Props {
  sessionRef: SessionRef
  summary: Schemas['SessionSummary']
  view: 'metrics' | 'logs'
}

/**
 * Metrics and logs are kept per config, so an initiator with backups picks which one to look at.
 */
export function SessionTelemetryTab({ sessionRef, summary, view }: Props) {
  const [configId, setConfigId] = useState(summary.runningConfigId)
  const configRef = { ...sessionRef, sessionId: configId }
  const navigate = useNavigate()

  return (
    <Flex vertical gap={16}>
      <Flex gap={8} align="center" justify="space-between" wrap>
        {summary.configIds.length > 1 ? (
          <Flex gap={8} align="center">
            <Typography.Text type="secondary">Config</Typography.Text>
            <Segmented
              value={configId}
              onChange={setConfigId}
              options={summary.configIds.map((id) => ({ value: id, label: id === summary.runningConfigId ? `${id} (selected)` : id }))}
            />
          </Flex>
        ) : (
          <span />
        )}
        {view === 'metrics' && (
          <Button size="small" icon={<ExpandAltOutlined />} onClick={() => navigate(metricsRoute(configRef))}>
            Open in Metrics
          </Button>
        )}
      </Flex>
      {view === 'metrics' ? (
        <MetricsPanel key={configId} scope={configRef} />
      ) : (
        <LogsPanel key={configId} engineId={configRef.engineId} group={configRef.group} sessionId={configRef.sessionId} />
      )}
    </Flex>
  )
}
