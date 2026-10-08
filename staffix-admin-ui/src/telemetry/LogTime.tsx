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
