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
