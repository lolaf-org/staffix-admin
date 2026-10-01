import { Alert, Badge, Card, Empty, Flex, Input, Select, Skeleton, Space, Tag, Typography } from 'antd'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router'
import type { Schemas } from '../api/client'
import { useEngines, useSessions } from '../api/queries'
import { HEALTH_LABELS, HEALTH_STATUS, HEALTHS, type Health } from '../sessions/health'
import { SessionBox } from '../sessions/SessionBox'
import { useSessionFilters } from '../sessions/filters'

export function OverviewPage() {
  const engines = useEngines()
  const filters = useSessionFilters()

  if (engines.isPending) return <Skeleton active />
  if (engines.isError) return <Alert type="error" title={engines.error.message} />

  const shown = engines.data.filter((e) => filters.engines.length === 0 || filters.engines.includes(e.instanceId))

  return (
    <Flex vertical gap={16}>
      <Flex wrap gap={8}>
        <Input.Search
          placeholder="Session id or FIX session id"
          allowClear
          defaultValue={filters.text}
          onChange={(e) => filters.setText(e.target.value)}
          style={{ width: 280, maxWidth: '100%' }}
        />
        <Select<Health[]>
          mode="multiple"
          allowClear
          placeholder="Any status"
          value={filters.healths}
          onChange={filters.setHealths}
          options={HEALTHS.map((h) => ({ value: h, label: <Badge status={HEALTH_STATUS[h]} text={HEALTH_LABELS[h]} /> }))}
          style={{ minWidth: 220, maxWidth: '100%' }}
        />
        <Select<string[]>
          mode="multiple"
          allowClear
          placeholder="Any engine"
          value={filters.engines}
          onChange={filters.setEngines}
          options={engines.data.map((e) => ({ value: e.instanceId, label: e.instanceId }))}
          style={{ minWidth: 200, maxWidth: '100%' }}
        />
      </Flex>
      {shown.length === 0 && <Empty description="No engine has announced itself yet" />}
      {shown.map((engine) => (
        <EngineCard key={engine.instanceId} engine={engine} />
      ))}
      <Outlet />
    </Flex>
  )
}

function EngineCard({ engine }: { engine: Schemas['Engine'] }) {
  const sessions = useSessions(engine.instanceId)
  const filters = useSessionFilters()
  const navigate = useNavigate()
  const location = useLocation()
  const selected = useParams()

  const groups = (sessions.data ?? [])
    .map((group) => ({ ...group, sessions: group.sessions.filter(filters.matches) }))
    .filter((group) => group.sessions.length > 0)
  const counts = HEALTHS.map((health) => ({
    health,
    count: (sessions.data ?? []).flatMap((g) => g.sessions).filter((s) => s.health === health).length,
  }))

  return (
    <Card
      size="small"
      title={
        <Space wrap>
          <Typography.Text strong>{engine.instanceId}</Typography.Text>
          {engine.reachable ? <Tag color="success">reachable</Tag> : <Tag color="error">unreachable</Tag>}
          {engine.staffixVersion && <Typography.Text type="secondary">staffix {engine.staffixVersion}</Typography.Text>}
        </Space>
      }
      extra={
        <Space>
          {counts.map(({ health, count }) => (
            <Badge key={health} status={HEALTH_STATUS[health]} text={count} showZero title={HEALTH_LABELS[health]} />
          ))}
        </Space>
      }
    >
      {sessions.isPending && <Skeleton active paragraph={{ rows: 2 }} />}
      {sessions.isError && <Alert type="error" title={sessions.error.message} />}
      {sessions.isSuccess && groups.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No matching session" />}
      <Flex vertical gap={12}>
        {groups.map((group) => (
          <div key={group.group}>
            <Typography.Text type="secondary" strong>
              {group.group}
            </Typography.Text>
            <Flex wrap gap={8} style={{ marginTop: 6 }}>
              {group.sessions.map((session) => (
                <SessionBox
                  key={session.id}
                  session={session}
                  selected={selected.instanceId === engine.instanceId && selected.sessionId === session.id}
                  onClick={() =>
                    navigate({ pathname: `/engines/${engine.instanceId}/sessions/${session.id}`, search: location.search })
                  }
                />
              ))}
            </Flex>
          </div>
        ))}
      </Flex>
    </Card>
  )
}
