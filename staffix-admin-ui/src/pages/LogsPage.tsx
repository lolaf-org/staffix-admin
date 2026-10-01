import { useState } from 'react'
import { Card, Empty, Flex, Select } from 'antd'
import { useEngines, useSessions } from '../api/queries'
import { LogsPanel } from '../telemetry/LogsPanel'

export function LogsPage() {
  const engines = useEngines()
  const [instanceId, setInstanceId] = useState<string>()
  const [sessionId, setSessionId] = useState<string>()
  const engine = instanceId ?? engines.data?.[0]?.instanceId
  const sessions = useSessions(engine)

  return (
    <Card size="small" title="Message logs">
      <Flex vertical gap={12}>
        <Flex wrap gap={8}>
          <Select
            placeholder="Engine"
            value={engine}
            onChange={(value) => {
              setInstanceId(value)
              setSessionId(undefined)
            }}
            options={engines.data?.map((e) => ({ value: e.instanceId, label: e.instanceId }))}
            style={{ width: 200 }}
          />
          <Select
            placeholder="Every session"
            allowClear
            showSearch
            value={sessionId}
            onChange={setSessionId}
            options={sessions.data?.flatMap((g) => g.sessions).map((s) => ({ value: s.id, label: s.id }))}
            style={{ width: 220 }}
          />
        </Flex>
        {engine ? <LogsPanel key={`${engine}-${sessionId}`} instanceId={engine} sessionId={sessionId} /> : <Empty />}
      </Flex>
    </Card>
  )
}
