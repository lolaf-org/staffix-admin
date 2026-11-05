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
import { Tag } from 'antd'
import type { HaNode } from './pairs'

export function NodeTag({ node, leads }: { node: HaNode; leads: boolean }) {
  if (!node.status) return <Tag color="error">{node.engineId}: unreachable</Tag>
  if (leads) return <Tag color="success">{node.engineId}: leader</Tag>
  if (node.status.role === 'UNKNOWN') return <Tag color="warning">{node.engineId}: no role</Tag>
  return <Tag>{node.engineId}: standby</Tag>
}
