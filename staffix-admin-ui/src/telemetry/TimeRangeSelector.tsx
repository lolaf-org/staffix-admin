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
import { Badge, DatePicker, Flex, Segmented } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { lastMinutes, type FixedPeriod } from '../api/queries'
import { LIVE_WINDOW, TIME_WINDOWS } from './timeWindows'

/**
 * The last minutes up to now, or a fixed period.
 */
export type Period = number | FixedPeriod

const CUSTOM = 'custom' as const
const LIVE = 'live' as const
const MAX_DAYS = 31

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
          onChange(picked === LIVE ? LIVE_WINDOW : picked === CUSTOM ? lastMinutes(typeof value === 'number' ? value : 60) : picked)
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

function tooFarFrom(day: Dayjs, from: Dayjs | undefined): boolean {
  return from !== undefined && Math.abs(day.diff(from, 'day', true)) > MAX_DAYS
}
