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
import { CaretDownOutlined, CaretRightOutlined, CheckCircleFilled, CloseCircleFilled, ReloadOutlined, SwapOutlined, ToolOutlined, WarningFilled } from '@ant-design/icons'
import { Alert, Card, Collapse, Descriptions, Empty, Flex, Skeleton, Space, Table, Tag, theme, Tooltip, Typography } from 'antd'
import { useComponentProbes, useGroups, useHaPairs, useResync, useResyncSession, useSetCandidate, useSwitchover } from '../api/queries'
import { AlertLevelTag } from '../alerts/AlertLevelTag'
import { ClearComponentAlertButton } from '../alerts/ClearAlertButton'
import { useFolded } from '../layout/useFolded'
import { WriteButton } from '../sessions/WriteButton'
import { HaCharts } from './HaCharts'
import { NodeTag } from './NodeTag'
import { leaderOf, shownNode, standbyState, type HaNode, type HaPair, type StandbyState } from './pairs'

/**
 * Past this a follower reads as stalled, whatever its last acknowledgement says: `inSync` is as of that acknowledgement.
 */
const STALLED_LAG_MILLIS = 3000
const HA_COMPONENT = 'ha'

export function HaPage() {
  const pairs = useHaPairs()

  if (pairs.isPending) return <Skeleton active />
  if (pairs.isError) return <Alert type="error" title={pairs.error.message} />
  if (pairs.data.length === 0) return <Empty description="No engine has high availability" />

  return (
    <Flex vertical gap={16}>
      {pairs.data.map((pair) => (
        <PairCard key={pair.name ?? pair.nodes[0]?.engineId} pair={pair} />
      ))}
    </Flex>
  )
}

function PairCard({ pair }: { pair: HaPair }) {
  const leader = leaderOf(pair)
  const [folded, toggleFolded] = useFolded(pair.name ?? pair.nodes[0]!.engineId)
  return (
    <Card
      size="small"
      styles={{ body: folded ? { display: 'none' } : undefined }}
      title={
        <Space wrap>
          <span
            role="button"
            aria-expanded={!folded}
            aria-label={folded ? 'Show the pair' : 'Hide the pair'}
            onClick={toggleFolded}
            style={{ cursor: 'pointer' }}
          >
            {folded ? <CaretRightOutlined /> : <CaretDownOutlined />}
          </span>
          {pair.name ? <span><Typography.Text type="secondary">HA pair: </Typography.Text><Typography.Text strong>{pair.name}</Typography.Text></span> : pair.nodes[0]?.engineId}
          {pair.nodes.map((node) => <NodeTag key={node.engineId} node={node} leads={node === leader} />)}
        </Space>
      }
    >
      {!folded && <Flex vertical gap={12}>
        {!leader && <Alert type="warning" title="No leader: the sessions are down until a node is elected" />}
        {!pair.name && <Alert type="info" title="High availability without a pair name: set pairName to group this node with its pair" />}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 420px), 1fr))', gap: 12 }}>
          {pair.nodes.map((node) => (
            <NodeCard key={node.engineId} node={node} leads={node === leader} />
          ))}
        </div>
        <Collapse
          size="small"
          defaultActiveKey={['sessions']}
          destroyOnHidden
          items={[{ key: 'sessions', label: 'Sessions', children: <PairSessions pair={pair} /> }]}
        />
        <HaCharts pair={pair} />
      </Flex>}
    </Card>
  )
}

function NodeCard({ node, leads }: { node: HaNode; leads: boolean }) {
  const status = node.status
  return (
    <Card size="small" type="inner" title={<NodeTag node={node} leads={leads} />} extra={status && <NodeActions node={node} leads={leads} />}>
      {!status && <Typography.Text type="secondary">This engine does not answer</Typography.Text>}
      {status && (
        <Flex vertical gap={8}>
          <Descriptions
            size="small"
            column={2}
            items={[
              { key: 'node', label: 'Node id', children: status.nodeId },
              { key: 'epoch', label: 'Epoch', children: status.epoch < 0 ? 'unknown' : status.epoch },
              { key: 'valid', label: 'Leadership', children: leads ? (status.leadershipValid ? 'proven' : 'cannot be proven') : 'n/a' },
              { key: 'since', label: 'Role since', children: status.roleSince ? new Date(status.roleSince).toLocaleTimeString() : 'none yet' },
              { key: 'candidate', label: 'Elections', children: status.candidate ? 'stands' : <Tag color="warning">in maintenance</Tag> },
              { key: 'election', label: 'Election', children: status.leaderElection },
            ]}
          />
          {status.followers.map((follower) => (
            <FollowerLine key={`${follower.nodeId}-${follower.link}`} follower={follower} />
          ))}
          {status.droppedFollowers.map((dropped) => (
            <Alert
              key={`${dropped.nodeId}-${dropped.link}`}
              type="error"
              title={`Follower ${dropped.nodeId} dropped as too slow at ${new Date(dropped.droppedAt).toLocaleTimeString()}${dropped.heldOffUntil ? `, held off until ${new Date(dropped.heldOffUntil).toLocaleTimeString()}` : ''}`}
            />
          ))}
          <NodeProbes engineId={node.engineId} />
          {status.leader && (
            <Typography.Text>
              <Health ok={status.leader.connected && status.leader.inSync} />
              Leader link {status.leader.address}: {!status.leader.connected ? 'not connected' : status.leader.inSync ? 'holds every session in full' : 'catching up'}
            </Typography.Text>
          )}
        </Flex>
      )}
    </Card>
  )
}

function NodeProbes({ engineId }: { engineId: string }) {
  const probes = useComponentProbes(engineId, HA_COMPONENT)
  if (!probes.data || probes.data.length === 0) return null
  return (
    <Flex vertical gap={4}>
      {probes.data.map((probe) => (
        <Flex key={probe.name} gap={6} align="center" wrap>
          <Tooltip title={probe.description}>
            <Typography.Text code>{probe.name}</Typography.Text>
          </Tooltip>
          {probe.level ? <AlertLevelTag level={probe.level} /> : <Typography.Text type="secondary">normal</Typography.Text>}
          {probe.message && <Typography.Text>{probe.message}</Typography.Text>}
          {probe.level && <ClearComponentAlertButton engineId={engineId} component={HA_COMPONENT} probe={probe.name} />}
        </Flex>
      ))}
    </Flex>
  )
}

type Follower = NonNullable<HaNode['status']>['followers'][number]

function FollowerLine({ follower }: { follower: Follower }) {
  const stalled = follower.lagMillis > STALLED_LAG_MILLIS
  const state = !follower.connected ? 'not connected'
    : follower.snapshotInProgress ? 'catching up by snapshot'
      : stalled ? `stalled for ${Math.round(follower.lagMillis / 1000)}s, in sync only as of its last ack`
        : follower.inSync ? 'in sync' : 'not in sync'
  return (
    <Typography.Text type={stalled ? 'danger' : undefined} strong={stalled}>
      <Health ok={follower.connected && follower.inSync && !stalled} />
      Follower {follower.nodeId ?? '?'}, link {follower.link}: {state}
      {follower.unsentBytes > 0 && `, ${Math.round(follower.unsentBytes / 1024)} KiB unsent`}
    </Typography.Text>
  )
}

function Health({ ok }: { ok: boolean }) {
  const { token } = theme.useToken()
  return ok
    ? <CheckCircleFilled style={{ color: token.colorSuccess, marginRight: 6 }} />
    : <CloseCircleFilled style={{ color: token.colorError, marginRight: 6 }} />
}

function NodeActions({ node, leads }: { node: HaNode; leads: boolean }) {
  const switchover = useSwitchover(node.engineId)
  const candidacy = useSetCandidate(node.engineId)
  const resync = useResync(node.engineId)
  const status = node.status!
  const followerInSync = status.followers.some((follower) => follower.inSync)
  return (
    <Flex gap={6}>
      {leads && (
        <Tooltip title={!status.transferLeadershipSupported ? 'The leader election cannot hand leadership over' : !followerInSync ? 'No follower is in sync' : undefined}>
          <span>
            <WriteButton
              size="small"
              icon={<SwapOutlined />}
              disabled={!status.transferLeadershipSupported || !followerInSync}
              loading={switchover.isPending}
              onConfirm={() => switchover.mutate(undefined)}
              confirm={`Switch over from ${node.engineId}? Its sessions log out and log on from the other node.`}
            >
              Switchover
            </WriteButton>
          </span>
        </Tooltip>
      )}
      {status.candidacySupported && (
        <WriteButton
          size="small"
          icon={<ToolOutlined />}
          loading={candidacy.isPending}
          onConfirm={() => candidacy.mutate(!status.candidate)}
          confirm={status.candidate ? `Put ${node.engineId} in maintenance? It stops standing for election.` : `End maintenance on ${node.engineId}?`}
        >
          {status.candidate ? 'Maintenance' : 'End maintenance'}
        </WriteButton>
      )}
      {leads && (
        <WriteButton
          size="small"
          icon={<ReloadOutlined />}
          loading={resync.isPending}
          onConfirm={() => resync.mutate(undefined)}
          confirm="Re-sync every session? Each goes through a snapshot, so the standby is not protected meanwhile."
        >
          Re-sync all
        </WriteButton>
      )}
    </Flex>
  )
}

function PairSessions({ pair }: { pair: HaPair }) {
  const shown = shownNode(pair)
  const groups = useGroups(shown.engineId)
  const rows = (groups.data ?? []).flatMap((group) => group.sessions).map((session) => {
    const replicated = shown.sessions.find((s) => s.group === session.group && s.name === session.id)
    return {
      key: `${session.group}.${session.id}`,
      group: session.group,
      id: session.id,
      replicated,
      standby: standbyState(pair, session.group, session.id),
    }
  })
  return (
    <Table
      size="small"
      pagination={false}
      dataSource={rows}
      columns={[
        { title: 'Session', key: 'session', render: (_, row) => row.key },
        { title: 'Mode', key: 'mode', render: (_, row) => row.replicated?.replicationMode ?? 'NONE' },
        { title: 'Standby', key: 'standby', render: (_, row) => <StandbyTag standby={row.standby} /> },
        {
          title: 'Unacknowledged sends',
          key: 'unacknowledged',
          render: (_, row) => row.replicated && (row.replicated.unprotected ? <Tag color="error">{row.replicated.unacknowledgedSends}</Tag> : row.replicated.unacknowledgedSends),
        },
        {
          title: '',
          key: 'resync',
          align: 'right',
          render: (_, row) => row.replicated && <ResyncButton engineId={shown.engineId} group={row.group} sessionId={row.id} />,
        },
      ]}
    />
  )
}

const STANDBY_TAG_COLORS = { ok: 'success', warning: 'warning', error: 'error' } as const

function StandbyTag({ standby }: { standby: StandbyState }) {
  return (
    <Tag color={STANDBY_TAG_COLORS[standby.health]} icon={standby.health === 'ok' ? undefined : <WarningFilled />}>
      {standby.text}
    </Tag>
  )
}

function ResyncButton({ engineId, group, sessionId }: { engineId: string; group: string; sessionId: string }) {
  const resync = useResyncSession({ engineId, group, sessionId })
  return (
    <WriteButton size="small" loading={resync.isPending} onConfirm={() => resync.mutate(undefined)} confirm={`Re-sync ${group}.${sessionId}?`}>
      Re-sync
    </WriteButton>
  )
}
