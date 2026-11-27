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
import type { Schemas } from '../api/client'

export type HaPair = Schemas['HaPair']
export type HaNode = Schemas['Node']

/**
 * A count below this is a live session's writes in flight, changing at each poll: nothing a takeover would wait for.
 */
const MAX_PENDING_WRITES = 1_000

export type SettingsDifference = NonNullable<HaNode['status']>['sessionSettingsDifferences'][number]

export const SETTINGS_DIFFERENCE_TEXT: Record<SettingsDifference['kind'], string> = {
  MISSING_HERE: 'settings not on the follower yet',
  DIFFERENT: 'settings differ on the follower',
  EXTRA_HERE: 'only on the follower',
}

export const REPLICATED_CHANGE_HINT =
  'Made on the leader, which passes it to its followers when this store is replicated. The HA page shows settings that still differ.'

export function leaderOf(pair: HaPair): HaNode | undefined {
  return pair.nodes.find((node) => node.status?.role === 'LEADER')
}

/**
 * The node whose sessions the overview shows: a pair without a leader still shows what a node last knows.
 */
export function shownNode(pair: HaPair): HaNode {
  return leaderOf(pair) ?? pair.nodes.find((node) => node.status) ?? pair.nodes[0]!
}

/**
 * A node that stops answering loses its status, and the id with it.
 */
export function nodeName(pair: HaPair, nodeId: string | null | undefined): string {
  return pair.nodes.find((node) => node.status?.nodeId === nodeId)?.engineId ?? `node ${nodeId ?? '?'}`
}

export function pairOf(pairs: HaPair[], engineId: string): HaPair | undefined {
  return pairs.find((pair) => pair.nodes.some((node) => node.engineId === engineId))
}

/**
 * Whether a failover to this node keeps what `SYNC` sends acknowledged: named by another node's `syncFollowers`, or the
 * pair names none, which makes every follower one.
 */
export function isSyncFollower(pair: HaPair, node: HaNode): boolean {
  const others = pair.nodes.filter((other) => other !== node && other.status)
  if (others.every((other) => other.status!.syncFollowers.length === 0)) return true
  return node.status != null && others.some((other) => other.status!.syncFollowers.includes(node.status!.nodeId))
}

/**
 * The sync followers the leader may switch over to now, the one it picks first: in sync on every lane, the fewest
 * writes left to store across their sessions, as the engine chooses.
 */
export function switchoverCandidates(pair: HaPair, leader: HaNode): HaNode[] {
  const connections = leader.status?.followerConnections ?? []
  const ready = (node: HaNode) => {
    const lanes = connections.filter((connection) => connection.nodeId === node.status?.nodeId)
    return lanes.length > 0 && lanes.every((lane) => lane.connected && lane.inSync && lane.syncFollower)
  }
  const pendingWrites = (node: HaNode) => node.sessions.reduce((sum, session) => sum + session.pendingWrites, 0)
  return pair.nodes.filter((node) => node !== leader && node.status && ready(node))
    .sort((one, other) => pendingWrites(one) - pendingWrites(other))
}

export type LanePosition = NonNullable<HaNode['status']>['lanePositions'][number]

/**
 * How many bytes of the lane's log `position` holds beyond `reference`, negative when behind; undefined when they came
 * from different leaderships, as offsets are in the log of the leadership that sent them.
 */
export function positionGap(reference: LanePosition, position: LanePosition): number | undefined {
  if (reference.epoch !== position.epoch || reference.leadership !== position.leadership) return undefined
  return position.offset - reference.offset
}

export function kib(bytes: number): string {
  return `${Math.ceil(bytes / 1024).toLocaleString('en-US')} KiB`
}

type Session = HaNode['sessions'][number]

function sessionOn(node: HaNode | undefined, group: string, sessionId: string): Session | undefined {
  return node?.sessions.find((session) => session.group === group && session.name === sessionId)
}

export interface ReplicationState {
  text: string
  /**
   * What a failover would cost: nothing, or a session the operator should look at. `none` is a session configured not to
   * replicate: the follower does not protect it, by choice.
   */
  health: 'none' | 'ok' | 'warning' | 'error'
}

export function needsAttention(replication: ReplicationState): boolean {
  return replication.health === 'warning' || replication.health === 'error'
}

export interface FollowerReplication {
  node: HaNode
  sync: boolean
  replication: ReplicationState
}

const HEALTH_RANK: Record<ReplicationState['health'], number> = { none: 0, ok: 1, warning: 2, error: 3 }

function worst(followers: FollowerReplication[]): FollowerReplication {
  return followers.reduce((worse, follower) => (HEALTH_RANK[follower.replication.health] > HEALTH_RANK[worse.replication.health] ? follower : worse))
}

/**
 * How a session of the node the overview shows replicates, summed up: its worst sync follower, as a failover may go to
 * any of them, and a disaster-recovery copy only when it needs attention, as a warning.
 */
export function replicationState(pair: HaPair, group: string, sessionId: string): ReplicationState {
  const shown = shownNode(pair)
  if (!shown.status) return { text: 'unavailable', health: 'error' }
  // a node lists every session but those configured with replication mode NONE, even one a follower refuses
  if (!sessionOn(shown, group, sessionId)) return { text: 'off', health: 'none' }
  const followers = followerReplications(pair, group, sessionId)
  if (followers.length === 0) return { text: 'follower down', health: 'error' }
  const syncFollowers = followers.filter((follower) => follower.sync)
  const shownFollowers = [worst(syncFollowers.length > 0 ? syncFollowers : followers),
    ...followers.filter((follower) => !follower.sync && needsAttention(follower.replication))]
  const named = (follower: FollowerReplication) =>
    (followers.length > 1 ? `${follower.node.engineId}: ${follower.replication.text}` : follower.replication.text)
  const health = shownFollowers.slice(1).length > 0 && HEALTH_RANK[shownFollowers[0]!.replication.health] < HEALTH_RANK.warning
    ? 'warning' : shownFollowers[0]!.replication.health
  return { text: shownFollowers.map(named).join(', '), health }
}

/**
 * How the session replicates to each other node, an empty list when the shown node does not run it.
 */
export function followerReplications(pair: HaPair, group: string, sessionId: string): FollowerReplication[] {
  const shown = shownNode(pair)
  const onShown = sessionOn(shown, group, sessionId)
  if (!shown.status || !onShown) return []
  return pair.nodes
    .filter((node) => node !== shown)
    .map((node) => ({ node, sync: isSyncFollower(pair, node), replication: followerState(shown, onShown, node, group, sessionId) }))
}

function followerState(shown: HaNode, onShown: Session, follower: HaNode, group: string, sessionId: string): ReplicationState {
  const link = linkState(shown, onShown, follower, group, sessionId)
  const difference = followerSettingsDifference(follower, group, sessionId)
  if (!difference) return link
  return { text: `${link.text}, ${SETTINGS_DIFFERENCE_TEXT[difference.kind]}`, health: link.health === 'error' ? 'error' : 'warning' }
}

function followerSettingsDifference(follower: HaNode, group: string, sessionId: string): SettingsDifference | undefined {
  if (follower.status?.role !== 'FOLLOWER') return undefined
  const qualifiedName = `${group}.${sessionId}:`
  return follower.status.sessionSettingsDifferences.find((difference) => difference.fixSessionId.startsWith(qualifiedName))
}

function linkState(shown: HaNode, onShown: Session, follower: HaNode, group: string, sessionId: string): ReplicationState {
  if (!follower.status) return { text: 'down', health: 'error' }
  const onFollower = sessionOn(follower, group, sessionId)
  const refusal = onFollower?.notReplicatedReason ?? onShown.notReplicatedReason
  if (refusal) return { text: `refused (${refusal})`, health: 'error' }
  if (!onFollower) return { text: 'not started', health: 'error' }
  const behind = onFollower.pendingWrites > MAX_PENDING_WRITES
  const pending = behind ? `, ${onFollower.pendingWrites.toLocaleString('en-US')} writes pending` : ''
  const lost = lostWrites(shown, onShown) + lostWrites(follower, onFollower)
  if (!onFollower.handedOver && !onFollower.inSync) return { text: `catching up${pending}${lost}`, health: 'warning' }
  return { text: `${onFollower.handedOver ? 'handed over' : 'in sync'}${pending}${lost}`, health: behind || lost ? 'warning' : 'ok' }
}

/**
 * Per node: each store can hold what the other lost, and a re-sync makes the follower match the leader, holes included.
 */
function lostWrites(node: HaNode, session: Session): string {
  if (session.lostWrites === 0) return ''
  return `, ${session.lostWrites.toLocaleString('en-US')} ${session.lostWrites === 1 ? 'write' : 'writes'} lost on ${node.engineId}`
}

/**
 * Why a session action cannot run on this engine; undefined when it leads or has no high availability.
 */
export function notLeadingReason(pairs: HaPair[], engineId: string): string | undefined {
  const pair = pairOf(pairs, engineId)
  if (!pair) return undefined
  const leader = leaderOf(pair)
  if (leader?.engineId === engineId) return undefined
  return leader ? `${engineId} is a follower: use the leader, ${leader.engineId}` : `${engineId} does not lead, and its group has no leader`
}

/**
 * p99 only when every node listing the timer publishes its histogram, as one chart mixing p99 and means would mislead.
 * A node that does not list it (down, or the timer not registered yet) does not decide.
 */
export function readsP99(timer: string, nodesMeters: (Schemas['Meter'][] | undefined)[]): boolean {
  const listed = nodesMeters.flatMap((meters) => meters?.filter((meter) => meter.name === timer) ?? [])
  return listed.length > 0 && listed.every((meter) => meter.percentiles)
}
