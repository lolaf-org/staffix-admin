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
import { Typography } from 'antd'
import { logTimeParts } from './format'

/**
 * A narrow no-break space between the groups, so a cell never wraps inside its time.
 */
const GROUP_SEPARATOR = '\u202F'

export function LogTime({ timestamp, digits }: { timestamp: string; digits: number }) {
  const { millis, finer } = logTimeParts(timestamp, digits)
  return (
    <Typography.Text style={{ fontFamily: 'monospace', fontSize: 12, whiteSpace: 'nowrap' }}>
      {millis}
      {finer.length > 0 && <Typography.Text type="secondary" style={{ fontFamily: 'monospace', fontSize: 12 }}>{finer.map((group) => GROUP_SEPARATOR + group).join('')}</Typography.Text>}
    </Typography.Text>
  )
}
