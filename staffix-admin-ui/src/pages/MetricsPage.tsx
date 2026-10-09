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
import { Card, Empty, Flex, Select } from 'antd'
import { useSearchParams } from 'react-router'
import { useEngines, useGroups, type MetricScope } from '../api/queries'
import { MetricsPanel } from '../telemetry/MetricsPanel'
import { metricsRoute } from '../telemetry/routes'

/**
 * The scope lives in the URL, so a view can be shared and the session panel can link to it.
 */
export function MetricsPage() {
  const engines = useEngines()
  const [params, setParams] = useSearchParams()
  const engine = params.get('engine') ?? engines.data?.[0]?.engineId
  const group = params.get('group') ?? undefined
  const sessionId = params.get('session') ?? undefined
  const groups = useGroups(engine)
  const configIds = groups.data?.find((g) => g.group === group)?.sessions.flatMap((s) => s.configIds) ?? []

  const select = (scope: Partial<MetricScope>) =>
    setParams(new URL(metricsRoute({ engineId: scope.engineId ?? engine ?? '', ...scope }), window.location.origin).searchParams, {
      replace: true,
    })

  return (
    <Card size="small" title="Metrics">
      <Flex vertical gap={12}>
        <Flex wrap gap={8}>
          <Select
            placeholder="Engine"
            value={engine}
            onChange={(value) => select({ engineId: value })}
            options={engines.data?.map((e) => ({ value: e.engineId, label: e.engineId }))}
            style={{ width: 200 }}
            aria-label="Engine"
          />
          <Select
            placeholder="Every group"
            allowClear
            showSearch
            value={group}
            onChange={(value) => select({ engineId: engine, group: value })}
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
            onChange={(value) => select({ engineId: engine, group, sessionId: value })}
            options={configIds.map((id) => ({ value: id, label: id }))}
            style={{ width: 200 }}
            aria-label="Session"
          />
        </Flex>
        {engine ? (
          <MetricsPanel key={`${engine}-${group}-${sessionId}`} scope={{ engineId: engine, group, sessionId }} chartHeight={340} />
        ) : (
          <Empty />
        )}
      </Flex>
    </Card>
  )
}
