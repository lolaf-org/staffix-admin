import { DatePicker, Flex, Segmented } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import type { FixedPeriod } from '../api/queries'
import { TIME_WINDOWS } from './timeWindows'

/**
 * The last minutes up to now, or a fixed period.
 */
export type Period = number | FixedPeriod

const CUSTOM = 'custom'
const MAX_DAYS = 31

interface Props {
  value: Period
  onChange: (period: Period) => void
}

export function TimeRangeSelector({ value, onChange }: Props) {
  const custom = typeof value !== 'number'

  return (
    <Flex wrap gap={8} align="center">
      <Segmented<number | typeof CUSTOM>
        options={[...TIME_WINDOWS, { value: CUSTOM, label: 'Custom' }]}
        value={custom ? CUSTOM : value}
        onChange={(picked) => onChange(picked === CUSTOM ? lastMinutes(value) : picked)}
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
