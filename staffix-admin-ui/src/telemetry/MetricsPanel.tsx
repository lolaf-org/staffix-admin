import { useState } from 'react'
import { Alert, Empty, Flex, Segmented, Select, Skeleton, Typography } from 'antd'
import { useEffect } from 'react'
import { AreaChartOutlined, TableOutlined } from '@ant-design/icons'
import { useMetrics, type MetricKind, type MetricScope } from '../api/queries'
import type { Schemas } from '../api/client'
import { formatRateValue, formatSeconds } from './format'
import { MetricChart, SeriesLegend, niceTicks, type ChartSeries } from './MetricChart'
import { MetricTable } from './MetricTable'
import { messageTypeName, plottedMessageTypes, seriesColors } from './series'
import { useThemeMode } from '../theme/ThemeMode'
import { TIME_WINDOWS } from './timeWindows'

/**
 * RTT and clock offset are per session: over a group or an engine they would draw one line per session.
 */
const KINDS: { value: MetricKind; label: string; title: string; perSession?: boolean }[] = [
  { value: 'MESSAGE_RATE', label: 'Rate', title: 'Messages per second' },
  { value: 'READ_LATENCY', label: 'Read', title: 'Read latency' },
  { value: 'WRITE_LATENCY', label: 'Write', title: 'Write latency' },
  { value: 'DECODING_LATENCY', label: 'Decoding', title: 'Decoding latency' },
  { value: 'ENCODING_LATENCY', label: 'Encoding', title: 'Encoding latency' },
  { value: 'ROUND_TRIP_TIME', label: 'RTT', title: 'Round-trip time', perSession: true },
  { value: 'CLOCK_OFFSET', label: 'Clock offset', title: 'Clock offset, remote minus local', perSession: true },
]

const QUANTILES = [0.5, 0.9, 0.99, 0.999]
const LATENCIES: MetricKind[] = ['READ_LATENCY', 'WRITE_LATENCY', 'DECODING_LATENCY', 'ENCODING_LATENCY']
const DIRECTIONS = [
  { value: 'in', title: 'incoming' },
  { value: 'out', title: 'outgoing' },
]

interface Facet {
  title: string
  series: ChartSeries[]
}

interface Props {
  scope: MetricScope
  chartHeight?: number
}

export function MetricsPanel({ scope, chartHeight }: Props) {
  const [kind, setKind] = useState<MetricKind>('MESSAGE_RATE')
  const [quantile, setQuantile] = useState(0.99)
  const [minutes, setMinutes] = useState(60)
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const sessionScoped = scope.sessionId !== undefined
  const metrics = useMetrics(scope, kind, quantile, minutes)
  const colors = seriesColors(useThemeMode().dark)
  useEffect(() => {
    if (!sessionScoped && KINDS.find((k) => k.value === kind)?.perSession) {
      setKind('MESSAGE_RATE')
    }
  }, [sessionScoped, kind])
  const format = kind === 'MESSAGE_RATE' ? formatRateValue : formatSeconds
  const selected = KINDS.find((k) => k.value === kind)!
  const title = LATENCIES.includes(kind) ? `${selected.title}, p${quantile * 100}` : selected.title

  return (
    <Flex vertical gap={16}>
      <Segmented<MetricKind>
        options={KINDS.map((k) => ({
          value: k.value,
          label: k.label,
          disabled: k.perSession && !sessionScoped,
          title: k.perSession && !sessionScoped ? 'Pick a session' : k.title,
        }))}
        value={kind}
        onChange={setKind}
      />
      <Flex wrap gap={8}>
        {LATENCIES.includes(kind) && (
          <Select
            value={quantile}
            onChange={setQuantile}
            options={QUANTILES.map((q) => ({ value: q, label: `p${q * 100}` }))}
            style={{ width: 90 }}
            aria-label="Quantile"
          />
        )}
        <Segmented options={TIME_WINDOWS} value={minutes} onChange={setMinutes} />
        <Segmented
          value={view}
          onChange={setView}
          options={[
            { value: 'chart', icon: <AreaChartOutlined />, title: 'Chart' },
            { value: 'table', icon: <TableOutlined />, title: 'Table' },
          ]}
        />
      </Flex>
      {!sessionScoped && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Over every session of the {scope.group ? `group ${scope.group}` : `engine ${scope.engineId}`}: rates are summed, latencies
          are the worst session's.
        </Typography.Text>
      )}
      {metrics.isPending && <Skeleton active />}
      {metrics.isError && <Alert type="error" title={metrics.error.message} />}
      {metrics.isSuccess && metrics.data.length === 0 && <Empty description="No data for this period" />}
      {metrics.isSuccess && metrics.data.length > 0 && (() => {
        const facets = toFacets(metrics.data, title, colors)
        if (view === 'table') return <MetricTable facets={facets} format={format} />
        const perDirection = facets.length > 1
        const yTicks = perDirection ? niceTicks(Math.max(...facets.flatMap((f) => f.series.flatMap((s) => s.points.map((p) => p.value))))) : undefined
        return (
          <Flex vertical gap={8}>
            <SeriesLegend series={facets[0]!.series} />
            <Flex wrap gap={16}>
              {facets.map((facet) => (
                <MetricChart key={facet.title} title={facet.title} series={facet.series} format={format} yTicks={yTicks} height={chartHeight} />
              ))}
            </Flex>
          </Flex>
        )
      })()}
    </Flex>
  )
}

function toFacets(series: Schemas['MetricSeries'][], title: string, colors: string[]): Facet[] {
  const perMessageType = series.some((s) => s.labels['messageType'])
  if (!perMessageType) {
    return [{ title, series: series.map((s, i) => ({ key: `s${i}`, name: title, color: colors[0]!, points: s.points })) }]
  }
  const types = plottedMessageTypes(series.map((s) => s.labels['messageType']!))
  return DIRECTIONS.map((direction) => ({
    title: `${title}, ${direction.title}`,
    series: types.flatMap((type, slot): ChartSeries[] => {
      const match = series.find((s) => s.labels['messageType'] === type && s.labels['direction'] === direction.value)
      return match ? [{ key: `t${type}`, name: messageTypeName(type), color: colors[slot]!, points: match.points }] : []
    }),
  }))
}
