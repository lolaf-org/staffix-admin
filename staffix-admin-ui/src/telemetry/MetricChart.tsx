import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps, type TooltipValueType } from 'recharts'
import { Flex, Typography, theme } from 'antd'
import { formatClock, formatDateTime } from './format'

export interface ChartSeries {
  key: string
  name: string
  color: string
  points: { timestamp: string; value: number }[]
}

interface Props {
  title: string
  series: ChartSeries[]
  format: (value: number) => string
  /**
   * Shared by small multiples so they compare at a glance; unset for an automatic, possibly negative, range.
   */
  yTicks?: number[]
  height?: number
}

export function MetricChart({ title, series, format, yTicks, height = 220 }: Props) {
  const { token } = theme.useToken()
  const rows = mergeByTimestamp(series)
  const signed = yTicks === undefined && series.some((s) => s.points.some((p) => p.value < 0))
  const axis = { stroke: token.colorBorder, tick: { fill: token.colorTextSecondary, fontSize: 12 } }

  return (
    <div style={{ flex: '1 1 300px', minWidth: 0 }}>
      <Typography.Text strong>{title}</Typography.Text>
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={rows} margin={{ top: 12, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={token.colorBorderSecondary} />
          <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']} tickFormatter={formatClock} minTickGap={40} {...axis} />
          <YAxis
            tickFormatter={format}
            width={64}
            domain={yTicks ? [0, yTicks[yTicks.length - 1]!] : signed ? ['auto', 'auto'] : [0, 'auto']}
            ticks={yTicks}
            tickCount={5}
            {...axis}
          />
          {signed && <ReferenceLine y={0} stroke={token.colorTextTertiary} />}
          <Tooltip content={(props) => <ChartTooltip {...props} series={series} format={format} />} />
          {series.map((s) => (
            <Line
              key={s.key}
              dataKey={s.key}
              name={s.name}
              stroke={s.color}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
              dot={false}
              activeDot={{ r: 4, stroke: token.colorBgContainer, strokeWidth: 2 }}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function ChartTooltip({ active, label, payload, series, format }: TooltipContentProps<TooltipValueType, string | number> & Pick<Props, 'series' | 'format'>) {
  const { token } = theme.useToken()
  if (!active || !payload?.length) return null
  return (
    <div style={{ background: token.colorBgElevated, boxShadow: token.boxShadowSecondary, borderRadius: token.borderRadius, padding: '8px 12px' }}>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {formatDateTime(Number(label))}
      </Typography.Text>
      {series.map((s) => {
        const value = payload.find((p) => p.dataKey === s.key)?.value
        return value === undefined ? null : (
          <Flex key={s.key} gap={8} align="center" justify="space-between" style={{ minWidth: 200 }}>
            <Flex gap={6} align="center">
              <Swatch color={s.color} />
              <Typography.Text style={{ fontSize: 12 }}>{s.name}</Typography.Text>
            </Flex>
            <Typography.Text strong style={{ fontSize: 12 }}>
              {format(Number(value))}
            </Typography.Text>
          </Flex>
        )
      })}
    </div>
  )
}

export function SeriesLegend({ series }: { series: Pick<ChartSeries, 'key' | 'name' | 'color'>[] }) {
  if (series.length < 2) return null
  return (
    <Flex wrap gap={16}>
      {series.map((s) => (
        <Flex key={s.key} gap={6} align="center">
          <Swatch color={s.color} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {s.name}
          </Typography.Text>
        </Flex>
      ))}
    </Flex>
  )
}

function Swatch({ color }: { color: string }) {
  return <span aria-hidden style={{ display: 'inline-block', width: 14, height: 3, borderRadius: 2, background: color }} />
}

export function mergeByTimestamp(series: ChartSeries[]): Record<string, number>[] {
  const rows = new Map<number, Record<string, number>>()
  for (const s of series) {
    for (const point of s.points) {
      const t = Date.parse(point.timestamp)
      const row = rows.get(t) ?? { t }
      row[s.key] = point.value
      rows.set(t, row)
    }
  }
  return [...rows.values()].sort((a, b) => a['t']! - b['t']!)
}

/**
 * Ticks from 0 on a step of 1, 2 or 5 times a power of ten, the last one at or above the maximum.
 */
export function niceTicks(max: number, count = 4): number[] {
  if (max <= 0) return [0, 1]
  const rough = max / count
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 5, 10].find((m) => m * magnitude >= rough)! * magnitude
  const ticks = []
  for (let tick = 0; tick < max + step / 2 && ticks.length <= count + 1; tick += step) {
    ticks.push(Number(tick.toPrecision(12)))
  }
  return ticks[ticks.length - 1]! < max ? [...ticks, Number((ticks.length * step).toPrecision(12))] : ticks
}
