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
import { Alert, Card, Empty, Flex, Input, Select, Skeleton, Space, Tag, Tooltip, Typography, theme } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router'
import type { Schemas } from '../api/client'
import { sessionRoute, useEngines, useGroups } from '../api/queries'
import { STATUS_LABELS, STATUSES, type Status } from '../sessions/status'
import { AddSessionModal } from '../sessions/AddSessionModal'
import { StatusBadge } from '../sessions/StatusBadge'
import { SessionBox } from '../sessions/SessionBox'
import { useSessionFilters } from '../sessions/filters'
import { WriteButton } from '../sessions/WriteButton'

export function OverviewPage() {
  const engines = useEngines()
  const filters = useSessionFilters()

  if (engines.isPending) return <Skeleton active />
  if (engines.isError) return <Alert type="error" title={engines.error.message} />

  const shown = engines.data.filter((e) => filters.engines.length === 0 || filters.engines.includes(e.engineId))

  return (
    <Flex vertical gap={16}>
      <Flex wrap gap={8}>
        <Input.Search
          placeholder="Group, session, instance or comp id"
          allowClear
          defaultValue={filters.text}
          onChange={(e) => filters.setText(e.target.value)}
          style={{ width: 320, maxWidth: '100%' }}
        />
        <Select<Status[]>
          mode="multiple"
          allowClear
          placeholder="Any status"
          value={filters.statuses}
          onChange={filters.setStatuses}
          options={STATUSES.map((s) => ({ value: s, label: <StatusBadge status={s} /> }))}
          style={{ minWidth: 220, maxWidth: '100%' }}
        />
        <Select<string[]>
          mode="multiple"
          allowClear
          placeholder="Any engine"
          value={filters.engines}
          onChange={filters.setEngines}
          options={engines.data.map((e) => ({ value: e.engineId, label: e.engineId }))}
          style={{ minWidth: 200, maxWidth: '100%' }}
        />
      </Flex>
      {shown.length === 0 && <Empty description="No engine has announced itself yet" />}
      {shown.map((engine) => (
        <EngineCard key={engine.engineId} engine={engine} />
      ))}
      <Outlet />
    </Flex>
  )
}

function EngineCard({ engine }: { engine: Schemas['Engine'] }) {
  const groups = useGroups(engine.engineId)
  const filters = useSessionFilters()
  const [adding, setAdding] = useState(false)

  const shown = (groups.data ?? [])
    .map((group) => ({ ...group, sessions: group.sessions.filter(filters.matches) }))
    .filter((group) => group.sessions.length > 0)
  const counts = STATUSES.map((status) => ({
    status,
    count: (groups.data ?? []).flatMap((g) => g.sessions).filter((s) => s.status === status).length,
  }))

  return (
    <Card
      size="small"
      title={
        <Space wrap>
          <span>
            <Typography.Text type="secondary">Fix engine: </Typography.Text>
            <Tooltip title={engine.staffixVersion ? `staffix ${engine.staffixVersion}` : 'staffix version unknown'}>
              <Typography.Text strong>{engine.engineId}</Typography.Text>
            </Tooltip>
          </span>
          {engine.reachable ? <Tag color="success">reachable</Tag> : <Tag color="error">unreachable</Tag>}
        </Space>
      }
      extra={
        <Space>
          {counts.map(({ status, count }) => (
            <StatusBadge key={status} status={status} text={count} title={STATUS_LABELS[status]} />
          ))}
          <WriteButton size="small" icon={<PlusOutlined />} onConfirm={() => setAdding(true)}>
            Acceptor session
          </WriteButton>
          <AddSessionModal engineId={engine.engineId} open={adding} onClose={() => setAdding(false)} groups={groups.data ?? []} />
        </Space>
      }
    >
      {groups.isPending && <Skeleton active paragraph={{ rows: 2 }} />}
      {groups.isError && <Alert type="error" title={groups.error.message} />}
      {groups.isSuccess && shown.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No matching session" />}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 480px), 1fr))', gap: 12 }}>
        {shown.map((group) => (
          <GroupBox key={group.group} engineId={engine.engineId} group={group} />
        ))}
      </div>
    </Card>
  )
}

function GroupBox({ engineId, group }: { engineId: string; group: Schemas['SessionGroup'] }) {
  const { token } = theme.useToken()
  const navigate = useNavigate()
  const location = useLocation()
  const selected = useParams()

  return (
    <section
      aria-label={`Group ${group.group}`}
      style={{
        border: `1px dashed ${token.colorBorder}`,
        borderRadius: token.borderRadiusLG,
        padding: 12,
        background: token.colorFillQuaternary,
      }}
    >
      <Flex gap={6} align="baseline" style={{ marginBottom: 8 }}>
        <Typography.Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}>
          group
        </Typography.Text>
        <Typography.Text strong style={{ fontSize: 15 }}>
          {group.group}
        </Typography.Text>
      </Flex>
      <Flex wrap gap={8} align="flex-start">
        {group.sessions.map((session) => {
          const ref = { engineId, group: group.group, sessionId: session.id }
          return (
            <SessionBox
              key={session.id}
              session={session}
              selected={selected.engineId === engineId && selected.group === group.group && selected.sessionId === session.id}
              onClick={() => navigate({ pathname: sessionRoute(ref), search: location.search })}
            />
          )
        })}
      </Flex>
    </section>
  )
}
