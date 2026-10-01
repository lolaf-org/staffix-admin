import { useState } from 'react'
import { Flex, Segmented, Typography } from 'antd'
import type { Schemas } from '../api/client'
import type { SessionRef } from '../api/queries'
import { LogsPanel } from './LogsPanel'
import { MetricsTab } from './MetricsTab'

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

  return (
    <Flex vertical gap={16}>
      {summary.configIds.length > 1 && (
        <Flex gap={8} align="center">
          <Typography.Text type="secondary">Config</Typography.Text>
          <Segmented
            value={configId}
            onChange={setConfigId}
            options={summary.configIds.map((id) => ({ value: id, label: id === summary.runningConfigId ? `${id} (running)` : id }))}
          />
        </Flex>
      )}
      {view === 'metrics' ? (
        <MetricsTab key={configId} sessionRef={configRef} />
      ) : (
        <LogsPanel key={configId} engineId={configRef.engineId} group={configRef.group} sessionId={configRef.sessionId} />
      )}
    </Flex>
  )
}
