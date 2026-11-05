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
import { Collapse, Flex, Typography } from 'antd'
import { useState } from 'react'
import { useMetrics, type MetricRequest } from '../api/queries'
import { MetricChart, SeriesLegend, type ChartSeries } from '../telemetry/MetricChart'
import { formatRateValue, formatSeconds } from '../telemetry/format'
import { seriesColors } from '../telemetry/series'
import { TimeRangeSelector, type Period } from '../telemetry/TimeRangeSelector'
import { useThemeMode } from '../theme/ThemeMode'
import { shownNode, type HaPair } from './pairs'

const ROLE_NAMES: Record<number, string> = { 2: 'leader', 1: 'follower', 0: 'unknown' }

function formatBytes(bytes: number): string {
  if (bytes >= 1 << 20) return `${(bytes / (1 << 20)).toFixed(1)} MiB`
  if (bytes >= 1 << 10) return `${(bytes / (1 << 10)).toFixed(0)} KiB`
  return `${bytes.toFixed(0)} B`
}

interface ChartSpec {
  title: string
  request: Extract<MetricRequest, { kind: 'meter' }>
  format: (value: number) => string
}

function meter(name: string, statistic: ChartSpec['request']['statistic'], splitBy: string[] = [], gaugeUnit?: ChartSpec['request']['gaugeUnit']): ChartSpec['request'] {
  return { kind: 'meter', meter: name, statistic, splitBy, gaugeUnit }
}

/**
 * Means, not percentiles: staffix's high availability timers publish a percentile histogram only when asked to.
 */
const LATENCY_CHARTS: ChartSpec[] = [
  { title: 'Follower lag (mean)', request: meter('ha.follower.lag', 'MEAN'), format: formatSeconds },
  { title: 'Current lag', request: meter('ha.follower.lag.pending', 'VALUE', [], 'MILLISECONDS'), format: formatSeconds },
  { title: 'Follower ack latency (mean)', request: meter('ha.follower.ack.latency', 'MEAN'), format: formatSeconds },
  { title: 'SYNC ack latency by mode (mean)', request: meter('ha.sync.ack.latency', 'MEAN', ['mode']), format: formatSeconds },
  { title: 'Follower store write (mean)', request: meter('ha.replica.received.write.latency', 'MEAN'), format: formatSeconds },
]

const FLOW_CHARTS: ChartSpec[] = [
  { title: 'Replication throughput', request: meter('ha.log.written', 'COUNTER_RATE'), format: (value) => `${formatBytes(value)}/s` },
  { title: 'Unsent to the follower', request: meter('ha.follower.unsent', 'VALUE'), format: formatBytes },
  { title: 'Unacknowledged SYNC sends by reason', request: meter('ha.sync.unacknowledged', 'COUNTER_RATE', ['reason']), format: formatRateValue },
  { title: 'Snapshots', request: meter('ha.snapshots', 'COUNTER_RATE'), format: formatRateValue },
  { title: 'Followers dropped as too slow', request: meter('ha.follower.too.slow', 'COUNTER_RATE'), format: formatRateValue },
  { title: 'Send buffers exhausted', request: meter('ha.follower.send.buffers.exhausted', 'COUNTER_RATE'), format: formatRateValue },
  { title: 'Role', request: meter('ha.role', 'VALUE'), format: (value) => ROLE_NAMES[Math.round(value)] ?? String(value) },
]

/**
 * Four charts a row, fewer once a chart would drop under 280 px.
 */
const CHART_GRID = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(max(280px, calc(25% - 12px)), 1fr))',
  gap: 16,
}

/**
 * Folded, the charts are unmounted, so a pair nobody looks at runs no metric query.
 */
export function HaCharts({ pair }: { pair: HaPair }) {
  const [period, setPeriod] = useState<Period>(15)
  const [live, setLive] = useState(true)
  const charts = (specs: ChartSpec[]) => specs.map((spec) => <PairChart key={spec.title} pair={pair} spec={spec} period={period} live={live} />)
  return (
    <Collapse
      size="small"
      defaultActiveKey={['charts']}
      destroyOnHidden
      items={[{
        key: 'charts',
        label: 'Replication charts',
        children: (
          <Flex vertical gap={8}>
            <TimeRangeSelector value={period} onChange={setPeriod} live={live} onLiveChange={setLive} />
            <div style={CHART_GRID}>{charts([...LATENCY_CHARTS, ...FLOW_CHARTS])}</div>
          </Flex>
        ),
      }]}
    />
  )
}

interface PairChartProps {
  pair: HaPair
  spec: ChartSpec
  period: Period
  live: boolean
}

/**
 * One series per node, so a chart reads on across a failover; a split chart shows the split on the shown node alone,
 * the two together being more series than a chart can plot.
 */
function PairChart({ pair, spec, period, live }: PairChartProps) {
  const { dark } = useThemeMode()
  const colors = seriesColors(dark)
  const split = spec.request.splitBy[0]
  const first = split ? shownNode(pair) : pair.nodes[0]!
  const second = split ? undefined : pair.nodes[1]
  const firstMetrics = useMetrics({ engineId: first.engineId }, spec.request, period, undefined, live)
  const secondMetrics = useMetrics({ engineId: second?.engineId ?? first.engineId }, second ? spec.request : undefined, period, undefined, live)

  const named = (engineId: string, metrics: typeof firstMetrics) =>
    (metrics.data?.series ?? []).map((s) => ({ name: split ? s.labels[split] ?? engineId : engineId, points: s.points }))
  const series: ChartSeries[] = [...named(first.engineId, firstMetrics), ...(second ? named(second.engineId, secondMetrics) : [])]
    .filter((s) => s.points.length > 0)
    .slice(0, colors.length)
    .map((s, index) => ({ key: s.name, name: s.name, color: colors[index]!, points: s.points }))

  return (
    <Flex vertical gap={4} style={{ minWidth: 0 }}>
      {series.length === 0
        ? <Typography.Text type="secondary">{spec.title}: no data</Typography.Text>
        : <>
            <MetricChart title={spec.title} series={series} format={spec.format} height={160} />
            <SeriesLegend series={series} />
          </>}
    </Flex>
  )
}
