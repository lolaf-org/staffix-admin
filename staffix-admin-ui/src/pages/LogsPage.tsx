import { useState } from 'react'
import { Card, Empty, Flex, Select } from 'antd'
import { useEngines, useGroups } from '../api/queries'
import { LogsPanel } from '../telemetry/LogsPanel'

export function LogsPage() {
  const engines = useEngines()
  const [engineId, setEngineId] = useState<string>()
  const [group, setGroup] = useState<string>()
  const [sessionId, setSessionId] = useState<string>()
  const engine = engineId ?? engines.data?.[0]?.engineId
  const groups = useGroups(engine)
  const configIds = groups.data?.find((g) => g.group === group)?.sessions.flatMap((s) => s.configIds) ?? []

  return (
    <Card size="small" title="Message logs">
      <Flex vertical gap={12}>
        <Flex wrap gap={8}>
          <Select
            placeholder="Engine"
            value={engine}
            onChange={(value) => {
              setEngineId(value)
              setGroup(undefined)
              setSessionId(undefined)
            }}
            options={engines.data?.map((e) => ({ value: e.engineId, label: e.engineId }))}
            style={{ width: 200 }}
            aria-label="Engine"
          />
          <Select
            placeholder="Every group"
            allowClear
            showSearch
            value={group}
            onChange={(value) => {
              setGroup(value)
              setSessionId(undefined)
            }}
            options={groups.data?.map((g) => ({ value: g.group, label: g.group }))}
            style={{ width: 200 }}
            aria-label="Group"
          />
          <Select
            placeholder="Every session"
            allowClear
            showSearch
            disabled={!group}
            value={sessionId}
            onChange={setSessionId}
            options={configIds.map((id) => ({ value: id, label: id }))}
            style={{ width: 200 }}
            aria-label="Session"
          />
        </Flex>
        {engine ? <LogsPanel key={`${engine}-${group}-${sessionId}`} engineId={engine} group={group} sessionId={sessionId} /> : <Empty />}
      </Flex>
    </Card>
  )
}
