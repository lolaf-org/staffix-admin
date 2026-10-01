import { useState } from 'react'
import { Alert, Empty, Flex, Segmented, Select, Skeleton } from 'antd'
import { AreaChartOutlined, TableOutlined } from '@ant-design/icons'
import { useMetrics, type MetricKind } from '../api/queries'
import type { Schemas } from '../api/client'
import { formatRateValue, formatSeconds } from './format'
import { MetricChart, SeriesLegend, niceTicks, type ChartSeries } from './MetricChart'
import { MetricTable } from './MetricTable'
import { SERIES_COLORS, messageTypeName, plottedMessageTypes } from './series'
import { TIME_WINDOWS } from './timeWindows'

const KINDS: { value: MetricKind; label: string; title: string }[] = [
  { value: 'MESSAGE_RATE', label: 'Rate', title: 'Messages per second' },
  { value: 'READ_LATENCY', label: 'Read', title: 'Read latency' },
  { value: 'WRITE_LATENCY', label: 'Write', title: 'Write latency' },
  { value: 'DECODING_LATENCY', label: 'Decoding', title: 'Decoding latency' },
  { value: 'ENCODING_LATENCY', label: 'Encoding', title: 'Encoding latency' },
  { value: 'ROUND_TRIP_TIME', label: 'RTT', title: 'Round-trip time' },
  { value: 'CLOCK_OFFSET', label: 'Clock offset', title: 'Clock offset, remote minus local' },
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

export function MetricsTab({ instanceId, sessionId }: { instanceId: string; sessionId: string }) {
  const [kind, setKind] = useState<MetricKind>('MESSAGE_RATE')
  const [quantile, setQuantile] = useState(0.99)
  const [minutes, setMinutes] = useState(60)
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const metrics = useMetrics(instanceId, sessionId, kind, quantile, minutes)
  const format = kind === 'MESSAGE_RATE' ? formatRateValue : formatSeconds
  const selected = KINDS.find((k) => k.value === kind)!
  const title = LATENCIES.includes(kind) ? `${selected.title}, p${quantile * 100}` : selected.title

  return (
    <Flex vertical gap={16}>
      <Segmented<MetricKind> options={KINDS} value={kind} onChange={setKind} />
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
      {metrics.isPending && <Skeleton active />}
      {metrics.isError && <Alert type="error" title={metrics.error.message} />}
      {metrics.isSuccess && metrics.data.length === 0 && <Empty description="No data for this period" />}
      {metrics.isSuccess && metrics.data.length > 0 && (() => {
        const facets = toFacets(metrics.data, title)
        if (view === 'table') return <MetricTable facets={facets} format={format} />
        const perDirection = facets.length > 1
        const yTicks = perDirection ? niceTicks(Math.max(...facets.flatMap((f) => f.series.flatMap((s) => s.points.map((p) => p.value))))) : undefined
        return (
          <Flex vertical gap={8}>
            <SeriesLegend series={facets[0]!.series} />
            <Flex wrap gap={16}>
              {facets.map((facet) => (
                <MetricChart key={facet.title} title={facet.title} series={facet.series} format={format} yTicks={yTicks} />
              ))}
            </Flex>
          </Flex>
        )
      })()}
    </Flex>
  )
}

function toFacets(series: Schemas['MetricSeries'][], title: string): Facet[] {
  const perMessageType = series.some((s) => s.labels['messageType'])
  if (!perMessageType) {
    return [{ title, series: series.map((s, i) => ({ key: `s${i}`, name: title, color: SERIES_COLORS[0]!, points: s.points })) }]
  }
  const types = plottedMessageTypes(series.map((s) => s.labels['messageType']!))
  return DIRECTIONS.map((direction) => ({
    title: `${title}, ${direction.title}`,
    series: types.flatMap((type, slot): ChartSeries[] => {
      const match = series.find((s) => s.labels['messageType'] === type && s.labels['direction'] === direction.value)
      return match ? [{ key: `t${type}`, name: messageTypeName(type), color: SERIES_COLORS[slot]!, points: match.points }] : []
    }),
  }))
}
