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
import { CaretDownOutlined, CaretRightOutlined, CheckCircleFilled, CloseCircleFilled, LockOutlined, ReloadOutlined, SwapOutlined, ToolOutlined, UnlockOutlined, WarningFilled } from '@ant-design/icons'
import { Alert, Card, Collapse, Descriptions, Empty, Flex, Radio, Skeleton, Space, Table, Tag, theme, Tooltip, Typography } from 'antd'
import { useState } from 'react'
import { useComponentProbes, useEngineAlerts, useGroups, useHaPairs, useResync, useResyncSession, useSetElectable, useSwitchover } from '../api/queries'
import { AlertLevelTag } from '../alerts/AlertLevelTag'
import { ClearComponentAlertButton } from '../alerts/ClearAlertButton'
import { useFolded } from '../layout/useFolded'
import { WriteButton } from '../sessions/WriteButton'
import { HaCharts } from './HaCharts'
import { NodeTag } from './NodeTag'
import { followerReplications, isSyncFollower, switchoverCandidates, kib, leaderOf, needsAttention, nodeName, positionGap, replicationState, SETTINGS_DIFFERENCE_TEXT, shownNode, type FollowerReplication, type HaNode, type HaPair, type LanePosition, type ReplicationState, type SettingsDifference } from './pairs'

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
            aria-label={folded ? 'Show the group' : 'Hide the group'}
            onClick={toggleFolded}
            style={{ cursor: 'pointer' }}
          >
            {folded ? <CaretRightOutlined /> : <CaretDownOutlined />}
          </span>
          {pair.name ? <span><Typography.Text type="secondary">HA group: </Typography.Text><Typography.Text strong>{pair.name}</Typography.Text></span> : pair.nodes[0]?.engineId}
          {pair.nodes.map((node) => <NodeTag key={node.engineId} node={node} leads={node === leader} />)}
        </Space>
      }
    >
      {!folded && <Flex vertical gap={12}>
        {!leader && <Alert type="warning" title="No leader: the sessions are down until a node is elected" />}
        {!leader && <LanePositions pair={pair} />}
        {!pair.name && <Alert type="info" title="High availability without a pair name: set pairName, the same on every node, to group this node with the others" />}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 420px), 1fr))', gap: 12 }}>
          {pair.nodes.map((node) => (
            <NodeCard key={node.engineId} pair={pair} node={node} leads={node === leader} />
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

function NodeCard({ pair, node, leads }: { pair: HaPair; node: HaNode; leads: boolean }) {
  const status = node.status
  const lanes = new Set([...(status?.followerConnections ?? []), ...(status?.droppedFollowers ?? [])].map((follower) => follower.lane))
  const laneOf = (lane: number) => (lanes.size > 1 ? `, lane ${lane}` : '')
  const followers = status?.followerConnections ?? []
  const tagged = (status?.peers.length ?? 0) > 1 || followers.some((follower) => !follower.syncFollower)
  const syncFollowers = status?.syncFollowers ?? []
  const absentFollowers = (status?.peers ?? []).filter((nodeId) =>
    !followers.some((follower) => follower.nodeId === nodeId) && !status!.droppedFollowers.some((dropped) => dropped.nodeId === nodeId))
  return (
    <Card size="small" type="inner" title={<NodeTag node={node} leads={leads} />} extra={status && <NodeActions pair={pair} node={node} leads={leads} />}>
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
              {
                key: 'electable',
                label: 'Can lead',
                children: (
                  <Tooltip title={`Leader chosen by ${status.leaderElectionType}`}>
                    {status.electable ? <span>yes</span> : <Tag color="warning">no, in maintenance</Tag>}
                  </Tooltip>
                ),
              },
            ]}
          />
          {followers.map((follower) => (
            <FollowerLine
              key={`${follower.nodeId}-${follower.lane}`}
              follower={follower}
              name={`${nodeName(pair, follower.nodeId)}${laneOf(follower.lane)}`}
              tagged={tagged}
              behind={behind(pair, followers, follower)}
            />
          ))}
          {leads && absentFollowers.map((nodeId) => {
            const sync = syncFollowers.length === 0 || syncFollowers.includes(nodeId)
            return (
              <Typography.Text key={nodeId} type={sync ? 'danger' : 'warning'}>
                <Health ok={false} />
                Replicating to {nodeName(pair, nodeId)}: not connected {tagged && <SyncTag sync={sync} />}
              </Typography.Text>
            )
          })}
          {status.droppedFollowers.map((dropped) => (
            <Alert
              key={`${dropped.nodeId}-${dropped.lane}`}
              type="error"
              title={`${nodeName(pair, dropped.nodeId)}${laneOf(dropped.lane)} dropped as too slow at ${new Date(dropped.droppedAt).toLocaleTimeString()}${dropped.heldOffUntil ? `, held off until ${new Date(dropped.heldOffUntil).toLocaleTimeString()}` : ''}`}
            />
          ))}
          <NodeProbes engineId={node.engineId} />
          <NodeStoreAlerts engineId={node.engineId} />
          <SettingsDifferences differences={status.sessionSettingsDifferences} />
          {status.leader && (
            <Typography.Text>
              <Health ok={status.leader.connected && status.leader.inSync} />
              <Tooltip title={status.leader.address}>
                <span>Replicating from {nodeName(pair, status.leaderId)}: {!status.leader.connected ? 'not connected' : status.leader.inSync ? 'in sync' : 'catching up'}</span>
              </Tooltip>
              {status.leader.connected && <Encryption cipher={status.leader.cipher} />}
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

/**
 * A follower runs no session, so this is the only place its stores' alerts have.
 */
function NodeStoreAlerts({ engineId }: { engineId: string }) {
  const alerts = useEngineAlerts(engineId, true)
  const stored = alerts.data?.filter((alert) => alert.component == null && !alert.sessionListed) ?? []
  if (stored.length === 0) return null
  return (
    <Flex vertical gap={4}>
      {stored.map((alert) => (
        <Flex key={`${alert.group}/${alert.sessionId}/${alert.probe}`} gap={6} align="center" wrap>
          <Typography.Text code>{alert.probe}</Typography.Text>
          <AlertLevelTag level={alert.level} />
          <Typography.Text>{alert.group}.{alert.sessionId}: {alert.message}</Typography.Text>
        </Flex>
      ))}
    </Flex>
  )
}

function SettingsDifferences({ differences }: { differences: SettingsDifference[] }) {
  if (differences.length === 0) return null
  return (
    <Alert
      type="warning"
      title="Session settings that differ from the leader's: a failover runs this node's own"
      description={
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          {differences.map((difference) => (
            <li key={`${difference.store}/${difference.fixSessionType}/${difference.fixSessionId}`}>
              {difference.fixSessionId} (store {difference.store}): {SETTINGS_DIFFERENCE_TEXT[difference.kind]}
            </li>
          ))}
        </ul>
      }
    />
  )
}

type Follower = NonNullable<HaNode['status']>['followerConnections'][number]

/**
 * How far this follower is behind the one of its lane holding the most: two followers' offsets are in the same log.
 */
function behind(pair: HaPair, followers: Follower[], follower: Follower): string | undefined {
  if (!follower.connected || follower.appliedOffset < 0) return undefined
  const furthest = followers
    .filter((other) => other.lane === follower.lane)
    .reduce((best, other) => (other.appliedOffset > best.appliedOffset ? other : best), follower)
  if (furthest === follower) return undefined
  return `${kib(furthest.appliedOffset - follower.appliedOffset)} behind ${nodeName(pair, furthest.nodeId)}`
}

function FollowerLine({ follower, name, tagged, behind }: { follower: Follower; name: string; tagged: boolean; behind?: string }) {
  const stalled = follower.lagMillis > STALLED_LAG_MILLIS
  const state = !follower.connected ? 'not connected'
    : follower.snapshotInProgress ? 'catching up by snapshot'
      : stalled ? `stalled for ${Math.round(follower.lagMillis / 1000)}s, in sync only as of its last ack`
        : follower.inSync ? 'in sync' : 'not in sync'
  return (
    <Typography.Text type={stalled ? 'danger' : undefined} strong={stalled}>
      <Health ok={follower.connected && follower.inSync && !stalled} />
      Replicating to {name}: {state}
      {follower.connected && follower.inSync && follower.catchUpMillis != null && follower.catchUpMillis >= 0 && <CatchUp millis={follower.catchUpMillis} />}
      {follower.unsentBytes > 0 && `, ${Math.round(follower.unsentBytes / 1024)} KiB unsent`}
      {behind && `, ${behind}`}
      {follower.connected && <Encryption cipher={follower.cipher} />}
      {tagged && <> <SyncTag sync={follower.syncFollower} /></>}
    </Typography.Text>
  )
}

function CatchUp({ millis }: { millis: number }) {
  return (
    <Tooltip title="How long the follower's last return to sync took (connecting, a re-sync, a session added), snapshots included: a failover to it meanwhile would have lost writes">
      <span>, caught up in {millis < 1000 ? `${millis} ms` : `${(millis / 1000).toFixed(1)} s`}</span>
    </Tooltip>
  )
}

function SyncTag({ sync }: { sync: boolean }) {
  return sync
    ? <Tooltip title="SYNC sends wait for it: a failover to it, in sync, loses nothing they acknowledged"><Tag color="blue">sync</Tag></Tooltip>
    : <Tooltip title="SYNC sends do not wait for it: a failover to it may lose what they acknowledged"><Tag color="purple">async (DR)</Tag></Tooltip>
}

/**
 * With the leader down, what each node holds, to choose which to promote by hand.
 */
function LanePositions({ pair }: { pair: HaPair }) {
  const holders = pair.nodes.filter((node) => (node.status?.lanePositions.length ?? 0) > 0)
  if (holders.length < 2) return null
  const reference = holders.find((node) => isSyncFollower(pair, node)) ?? holders[0]!
  const lanes = [...new Set(holders.flatMap((node) => node.status!.lanePositions.map((position) => position.lane)))].sort((a, b) => a - b)
  const positionOf = (node: HaNode, lane: number) => node.status!.lanePositions.find((position) => position.lane === lane)
  return (
    <Alert
      type="info"
      title={`Where each node stands, compared with ${reference.engineId}${isSyncFollower(pair, reference) ? ', a sync follower' : ''}`}
      description={
        <Table
          size="small"
          pagination={false}
          rowKey="lane"
          dataSource={lanes.map((lane) => ({ lane }))}
          columns={[
            { title: 'Lane', dataIndex: 'lane' },
            ...holders.map((node) => ({
              title: <Space size={4}>{node.engineId}<SyncTag sync={isSyncFollower(pair, node)} /></Space>,
              key: node.engineId,
              render: (_: unknown, row: { lane: number }) => positionText(positionOf(reference, row.lane), positionOf(node, row.lane), node === reference),
            })),
          ]}
        />
      }
    />
  )
}

function positionText(reference: LanePosition | undefined, position: LanePosition | undefined, isReference: boolean): string {
  if (!position || position.offset < 0) return 'nothing since it last connected'
  if (isReference || !reference || reference.offset < 0) return `epoch ${position.epoch}, offset ${position.offset.toLocaleString('en-US')}`
  const gap = positionGap(reference, position)
  if (gap === undefined) return `from another leadership (epoch ${position.epoch}), not comparable`
  return gap === 0 ? 'level' : gap > 0 ? `${kib(gap)} ahead` : `${kib(-gap)} behind`
}

function Encryption({ cipher }: { cipher?: string | null }) {
  return cipher
    ? <Tooltip title={cipher}><span>, <LockOutlined /> encrypted</span></Tooltip>
    : <Tooltip title="Neither the messages nor the session settings are encrypted on this connection"><span>, <UnlockOutlined /> in clear</span></Tooltip>
}

function Health({ ok }: { ok: boolean }) {
  const { token } = theme.useToken()
  return ok
    ? <CheckCircleFilled style={{ color: token.colorSuccess, marginRight: 6 }} />
    : <CloseCircleFilled style={{ color: token.colorError, marginRight: 6 }} />
}

function NodeActions({ pair, node, leads }: { pair: HaPair; node: HaNode; leads: boolean }) {
  const switchover = useSwitchover(node.engineId)
  const electable = useSetElectable(node.engineId)
  const resync = useResync(node.engineId)
  const [successor, setSuccessor] = useState<string>()
  const status = node.status!
  const followerInSync = status.followerConnections.some((follower) => follower.inSync && follower.syncFollower)
  const candidates = leads ? switchoverCandidates(pair, node) : []
  return (
    <Flex gap={6}>
      {leads && (
        <Tooltip title={!status.transferLeadershipSupported ? 'The leader election cannot hand leadership over' : !followerInSync ? 'No sync follower is in sync' : undefined}>
          <span>
            <WriteButton
              size="small"
              icon={<SwapOutlined />}
              disabled={!status.transferLeadershipSupported || !followerInSync}
              loading={switchover.isPending}
              onConfirm={() => switchover.mutate(successor)}
              confirm={<SwitchoverConfirm from={node} candidates={candidates} successor={successor} onSuccessor={setSuccessor} />}
            >
              Switchover
            </WriteButton>
          </span>
        </Tooltip>
      )}
      {status.electableSupported && (
        <WriteButton
          size="small"
          icon={<ToolOutlined />}
          loading={electable.isPending}
          onConfirm={() => electable.mutate(!status.electable)}
          confirm={status.electable ? `Put ${node.engineId} in maintenance? It stops standing for election.` : `End maintenance on ${node.engineId}?`}
        >
          {status.electable ? 'Maintenance' : 'End maintenance'}
        </WriteButton>
      )}
      {leads && (
        <WriteButton
          size="small"
          icon={<ReloadOutlined />}
          loading={resync.isPending}
          onConfirm={() => resync.mutate(undefined)}
          confirm="Re-sync every session? Each goes through a snapshot, so the sessions are not protected meanwhile."
        >
          Re-sync all
        </WriteButton>
      )}
    </Flex>
  )
}

/**
 * With several sync followers ready, the operator may name one; left alone, the engine picks the most caught up.
 */
function SwitchoverConfirm({ from, candidates, successor, onSuccessor }: {
  from: HaNode
  candidates: HaNode[]
  successor: string | undefined
  onSuccessor: (nodeId: string | undefined) => void
}) {
  if (candidates.length < 2) {
    return `Switch over from ${from.engineId}? Its sessions log out and log on from ${candidates[0]?.engineId ?? 'a sync follower once one is ready'}.`
  }
  return (
    <Flex vertical gap={8}>
      <span>Switch over from {from.engineId}? Its sessions log out and log on from:</span>
      <Radio.Group
        value={successor ?? ''}
        onChange={(event) => onSuccessor(event.target.value || undefined)}
        options={[
          { value: '', label: `the most caught up, ${candidates[0]!.engineId} now` },
          ...candidates.map((candidate) => ({ value: candidate.status!.nodeId, label: candidate.engineId })),
        ]}
      />
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
      replication: replicationState(pair, session.group, session.id),
      followers: followerReplications(pair, session.group, session.id),
    }
  })
  const anyUnencrypted = rows.some((row) => (row.replicated?.unencryptedMessageTypes.length ?? 0) > 0)
  return (
    <Table
      size="small"
      pagination={false}
      dataSource={rows}
      columns={[
        { title: 'Session', key: 'session', render: (_, row) => row.key },
        { title: 'Mode', key: 'mode', render: (_, row) => modeText(row.replicated) },
        ...(anyUnencrypted ? [{
          title: <Tooltip title="The message types an encrypted replication leaves unencrypted, still authenticated">Unencrypted</Tooltip>,
          key: 'unencrypted',
          render: (_: unknown, row: (typeof rows)[number]) => unencryptedText(row.replicated?.unencryptedMessageTypes ?? []),
        }] : []),
        { title: 'Replication', key: 'replication', render: (_, row) => <FollowerTags followers={row.followers} summary={row.replication} /> },
        {
          title: 'Unacknowledged sends',
          key: 'unacknowledged',
          render: (_, row) => row.replicated && (row.replicated.lastSendUnacknowledged ? <Tag color="error">{row.replicated.unacknowledgedSends}</Tag> : row.replicated.unacknowledgedSends),
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

function modeText(session: HaNode['sessions'][number] | undefined): string {
  if (!session) return 'NONE'
  return session.replicationQuorum > 0 ? `${session.replicationMode}, quorum ${session.replicationQuorum}` : session.replicationMode
}

function unencryptedText(types: string[]): string {
  return types.includes('*') ? 'all but Logon' : types.map((type) => `35=${type}`).join(', ')
}

const REPLICATION_TAG_COLORS = { none: 'default', ok: 'success', warning: 'warning', error: 'error' } as const

/**
 * One tag per follower once there are several, so a disaster-recovery copy refusing a session shows too.
 */
function FollowerTags({ followers, summary }: { followers: FollowerReplication[]; summary: ReplicationState }) {
  if (followers.length < 2) return <ReplicationTag replication={summary} />
  return (
    <Space size={4} wrap>
      {followers.map((follower) => (
        <Tooltip key={follower.node.engineId} title={follower.sync ? 'Sync follower: a failover may go to it' : 'Disaster-recovery copy: SYNC sends do not wait for it'}>
          <span><ReplicationTag replication={{ ...follower.replication, text: `${follower.node.engineId}${follower.sync ? '' : ' (DR)'}: ${follower.replication.text}` }} /></span>
        </Tooltip>
      ))}
    </Space>
  )
}

function ReplicationTag({ replication }: { replication: ReplicationState }) {
  return (
    <Tag color={REPLICATION_TAG_COLORS[replication.health]} icon={needsAttention(replication) ? <WarningFilled /> : undefined}>
      {replication.text}
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
