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
import { Tooltip, Typography } from 'antd'
import { formatDateTime, formatDuration } from '../telemetry/format'

/**
 * How long an alert has lasted, the moment it was raised on hover: the duration is what is read at a glance, and a
 * full date in every row took the space the message needs.
 */
export function AlertSince({ since, now }: { since: string; now: number }) {
  const raisedAt = Date.parse(since)
  return (
    <Tooltip title={`Raised ${formatDateTime(raisedAt)}`}>
      <Typography.Text style={{ whiteSpace: 'nowrap' }}>{formatDuration(now - raisedAt)}</Typography.Text>
    </Tooltip>
  )
}
