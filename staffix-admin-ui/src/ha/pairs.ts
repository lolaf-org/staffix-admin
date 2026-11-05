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

export function leaderOf(pair: HaPair): HaNode | undefined {
  return pair.nodes.find((node) => node.status?.role === 'LEADER')
}

/**
 * The node whose sessions the overview shows: a pair without a leader still shows what a node last knows.
 */
export function shownNode(pair: HaPair): HaNode {
  return leaderOf(pair) ?? pair.nodes.find((node) => node.status) ?? pair.nodes[0]!
}

export function pairOf(pairs: HaPair[], engineId: string): HaPair | undefined {
  return pairs.find((pair) => pair.nodes.some((node) => node.engineId === engineId))
}

function sessionOn(node: HaNode | undefined, group: string, sessionId: string) {
  return node?.sessions.find((session) => session.group === group && session.name === sessionId)
}

export interface StandbyState {
  text: string
  /**
   * What a failover would cost: nothing, or a session the operator should look at.
   */
  health: 'ok' | 'warning' | 'error'
}

/**
 * Where the standby stands for a session of the node the overview shows.
 */
export function standbyState(pair: HaPair, group: string, sessionId: string): StandbyState {
  const shown = shownNode(pair)
  const standby = pair.nodes.find((node) => node !== shown)
  const onShown = sessionOn(shown, group, sessionId)
  if (!onShown) return { text: 'not replicated', health: 'warning' }
  if (!standby?.status) return { text: 'unavailable', health: 'error' }
  const onStandby = sessionOn(standby, group, sessionId)
  const refusal = onStandby?.notReplicatedReason ?? onShown.notReplicatedReason
  if (refusal) return { text: `refused (${refusal})`, health: 'error' }
  if (!onStandby) return { text: 'not held yet', health: 'error' }
  if (onStandby.handedOver) return { text: 'would take over cleanly', health: 'ok' }
  return onStandby.inSync ? { text: 'holds it in full', health: 'ok' } : { text: 'catching up', health: 'warning' }
}

/**
 * Why a session action cannot run on this engine; undefined when it leads or has no high availability.
 */
export function standbyReason(pairs: HaPair[], engineId: string): string | undefined {
  const pair = pairOf(pairs, engineId)
  if (!pair) return undefined
  const leader = leaderOf(pair)
  if (leader?.engineId === engineId) return undefined
  return leader ? `${engineId} is a standby: use the leader, ${leader.engineId}` : `${engineId} does not lead, and its pair has no leader`
}
