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

function sessionOn(node: HaNode | undefined, group: string, sessionId: string) {
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

/**
 * How a session of the node the overview shows replicates to the follower.
 */
export function replicationState(pair: HaPair, group: string, sessionId: string): ReplicationState {
  const shown = shownNode(pair)
  const follower = pair.nodes.find((node) => node !== shown)
  const onShown = sessionOn(shown, group, sessionId)
  if (!shown.status) return { text: 'unavailable', health: 'error' }
  // a node lists every session but those configured with replication mode NONE, even one a follower refuses
  if (!onShown) return { text: 'off', health: 'none' }
  if (!follower?.status) return { text: 'follower down', health: 'error' }
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
function lostWrites(node: HaNode, session: NonNullable<ReturnType<typeof sessionOn>>): string {
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
  return leader ? `${engineId} is a follower: use the leader, ${leader.engineId}` : `${engineId} does not lead, and its pair has no leader`
}
