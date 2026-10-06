import { Badge, DatePicker, Flex, Segmented } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import type { FixedPeriod } from '../api/queries'
import { TIME_WINDOWS } from './timeWindows'

/**
 * The last minutes up to now, or a fixed period.
 */
export type Period = number | FixedPeriod

const CUSTOM = 'custom' as const
const LIVE = 'live' as const
const MAX_DAYS = 31
const SHORTEST_WINDOW = 15

interface Props {
  value: Period
  onChange: (period: Period) => void
  /**
   * Whether the view follows the shortest window as it moves; given with `onLiveChange`, which offers it first.
   */
  live?: boolean
  onLiveChange?: (live: boolean) => void
}

export function TimeRangeSelector({ value, onChange, live = false, onLiveChange }: Props) {
  const custom = typeof value !== 'number'

  return (
    <Flex wrap gap={8} align="center">
      <Segmented<number | typeof CUSTOM | typeof LIVE>
        options={[
          ...(onLiveChange ? [{ value: LIVE, label: <Badge status={live ? 'processing' : 'default'} text="Live" /> }] : []),
          ...TIME_WINDOWS,
          { value: CUSTOM, label: 'Custom' },
        ]}
        value={live ? LIVE : custom ? CUSTOM : value}
        onChange={(picked) => {
          onLiveChange?.(picked === LIVE)
          if (picked === LIVE) {
            onChange(SHORTEST_WINDOW)
          } else {
            onChange(picked === CUSTOM ? lastMinutes(value) : picked)
          }
        }}
      />
      {custom && (
        <DatePicker.RangePicker
          showTime
          format="YYYY-MM-DD HH:mm:ss"
          allowClear={false}
          value={[dayjs(value.from), dayjs(value.to)]}
          onChange={(range) => range?.[0] && range[1] && onChange({ from: range[0].toISOString(), to: range[1].toISOString() })}
          disabledDate={(day, info) => day.isAfter(dayjs().endOf('day')) || tooFarFrom(day, info.from)}
          aria-label="Period"
        />
      )}
    </Flex>
  )
}

function lastMinutes(period: Period): FixedPeriod {
  const to = dayjs()
  return { from: to.subtract(typeof period === 'number' ? period : 60, 'minute').toISOString(), to: to.toISOString() }
}

function tooFarFrom(day: Dayjs, from: Dayjs | undefined): boolean {
  return from !== undefined && Math.abs(day.diff(from, 'day', true)) > MAX_DAYS
}
