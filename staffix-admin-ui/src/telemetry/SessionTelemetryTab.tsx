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
