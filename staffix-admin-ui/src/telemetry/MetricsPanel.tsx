import { useState } from 'react'
import { Alert, Empty, Flex, Segmented, Select, Skeleton, Typography } from 'antd'
import { useEffect } from 'react'
import { AreaChartOutlined, TableOutlined } from '@ant-design/icons'
import { useMessageTypes, useMetricKinds, useMetrics, type FixedPeriod, type MetricKind, type MetricScope } from '../api/queries'
import { isNotCollected, type Schemas } from '../api/client'
import { NotCollectedNotice } from './NotCollectedNotice'
import { formatRateValue, formatSeconds } from './format'
import { MetricChart, SeriesLegend, niceTicks, type ChartSeries } from './MetricChart'
import { MetricTable } from './MetricTable'
import { messageTypeName, plottedMessageTypes, rankedMessageTypes, SERIES_COUNT, seriesColors } from './series'
import { useThemeMode } from '../theme/ThemeMode'
import { TimeRangeSelector, type Period } from './TimeRangeSelector'
import { durationSeconds, parseDuration } from '../settings/duration'

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
const AUTO = 'auto'
const RATE_WINDOWS = ['PT1S', 'PT2S', 'PT5S', 'PT10S', 'PT30S', 'PT1M', 'PT5M']
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
  /**
   * Replaces the time range selector, e.g. around a logged message.
   */
  period?: FixedPeriod
  /**
   * An instant, in epoch milliseconds, marked on every chart.
   */
  marker?: number
}

export function MetricsPanel({ scope, chartHeight, period, marker }: Props) {
  const [kind, setKind] = useState<MetricKind>('MESSAGE_RATE')
  const [quantile, setQuantile] = useState(0.99)
  const [selectedPeriod, setSelectedPeriod] = useState<Period>(60)
  const [rateWindow, setRateWindow] = useState(AUTO)
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const [pickedTypes, setPickedTypes] = useState<string[]>([])
  const sessionScoped = scope.sessionId !== undefined
  const metrics = useMetrics(scope, kind, quantile, period ?? selectedPeriod, rateWindow === AUTO ? undefined : rateWindow)
  const minRateWindow = metrics.data?.minRateWindow ? durationSeconds(metrics.data.minRateWindow) ?? 0 : 0
  const measured = useMetricKinds(scope).data
  const isMeasured = (k: MetricKind) => measured === undefined || measured.includes(k)
  const colors = seriesColors(useThemeMode().dark)
  const dictionaryTypes = useMessageTypes(scope).data
  const typeName = (code: string) => messageTypeName(code, dictionaryTypes?.find((t) => t.msgType === code)?.name)
  const typesWithData = metrics.data?.series.flatMap((s) => (s.labels['messageType'] ? [s.labels['messageType']] : [])) ?? []
  const plottedTypes = plottedMessageTypes(typesWithData, pickedTypes)
  useEffect(() => {
    if (!sessionScoped && KINDS.find((k) => k.value === kind)?.perSession) {
      setKind('MESSAGE_RATE')
    } else if (!isMeasured(kind)) {
      const first = KINDS.find((k) => isMeasured(k.value) && (sessionScoped || !k.perSession))
      if (first) setKind(first.value)
    }
  }, [sessionScoped, kind, measured])
  const format = kind === 'MESSAGE_RATE' ? formatRateValue : formatSeconds
  const selected = KINDS.find((k) => k.value === kind)!
  const title = LATENCIES.includes(kind) ? `${selected.title}, p${quantile * 100}` : selected.title

  return (
    <Flex vertical gap={16}>
      <Segmented<MetricKind>
        options={KINDS.map((k) => ({
          value: k.value,
          label: k.label,
          disabled: (k.perSession && !sessionScoped) || !isMeasured(k.value),
          title: !isMeasured(k.value)
            ? "Not measured: this meter is off in the sessions' monitoring plugin"
            : k.perSession && !sessionScoped ? 'Pick a session' : k.title,
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
        {typesWithData.length > 0 && (
          <Select
            mode="multiple"
            allowClear
            showSearch
            maxCount={SERIES_COUNT}
            placeholder="Message types"
            value={plottedTypes}
            onChange={setPickedTypes}
            optionFilterProp="label"
            options={rankedMessageTypes(typesWithData).map((code) => ({ value: code, label: typeName(code) }))}
            style={{ minWidth: 260, maxWidth: '100%' }}
            aria-label={`Message types, up to ${SERIES_COUNT}`}
            title={`Up to ${SERIES_COUNT} message types; cleared, the ${SERIES_COUNT} first by order flow`}
          />
        )}
        {!period && <TimeRangeSelector value={selectedPeriod} onChange={setSelectedPeriod} />}
        {kind !== 'CLOCK_OFFSET' && (
          <Select
            value={rateWindow}
            onChange={setRateWindow}
            options={[
              { value: AUTO, label: 'Rate window: auto', title: 'The time between two points, or the narrowest the data allows' },
              ...RATE_WINDOWS.map((window) => {
                const tooNarrow = durationSeconds(window)! < minRateWindow
                return {
                  value: window,
                  label: `Rate window: ${durationLabel(window)}`,
                  disabled: tooNarrow,
                  title: tooNarrow
                    ? `The data is not finer than ${durationLabel(metrics.data!.minRateWindow!)}: a rate needs two samples, and the engines export or the backend samples that often`
                    : undefined,
                }
              }),
            ]}
            style={{ width: 170 }}
            aria-label="Rate window"
          />
        )}
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
      {metrics.data && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {metrics.data.rateWindow ? `Rates over ${durationLabel(metrics.data.rateWindow)}, a point` : 'A point'} every{' '}
          {durationLabel(metrics.data.step)}.
          {metrics.data.rateWindow && durationSeconds(metrics.data.rateWindow)! < durationSeconds(metrics.data.step)! &&
            ' The window is narrower than the time between points, so the chart skips some: a shorter period shows them all.'}
        </Typography.Text>
      )}
      {metrics.isPending && <Skeleton active />}
      {metrics.isError && (isNotCollected(metrics.error)
        ? <Empty description={metrics.error.message} />
        : <Alert type="error" title={metrics.error.message} />)}
      {metrics.isSuccess && <NotCollectedNotice what="metrics" sessions={metrics.data.notCollected} />}
      {metrics.isSuccess && metrics.data.series.length === 0 && <Empty description="No data for this period" />}
      {metrics.isSuccess && metrics.data.series.length > 0 && (() => {
        const facets = toFacets(metrics.data.series, title, colors, plottedTypes, typeName)
        if (view === 'table') return <MetricTable facets={facets} format={format} />
        const perDirection = facets.length > 1
        const yTicks = perDirection ? niceTicks(Math.max(...facets.flatMap((f) => f.series.flatMap((s) => s.points.map((p) => p.value))))) : undefined
        return (
          <Flex vertical gap={8}>
            <SeriesLegend series={facets[0]!.series} />
            <Flex wrap gap={16}>
              {facets.map((facet) => (
                <MetricChart key={facet.title} title={facet.title} series={facet.series} format={format} yTicks={yTicks} height={chartHeight} marker={marker} />
              ))}
            </Flex>
          </Flex>
        )
      })()}
    </Flex>
  )
}

function durationLabel(iso: string): string {
  const duration = parseDuration(iso)
  return duration ? `${duration.amount} ${duration.unit}` : iso
}

function toFacets(series: Schemas['MetricSeries'][], title: string, colors: string[], types: string[],
                  typeName: (code: string) => string): Facet[] {
  const perMessageType = series.some((s) => s.labels['messageType'])
  if (!perMessageType) {
    return [{ title, series: series.map((s, i) => ({ key: `s${i}`, name: title, color: colors[0]!, points: s.points })) }]
  }
  return DIRECTIONS.map((direction) => ({
    title: `${title}, ${direction.title}`,
    series: types.flatMap((type, slot): ChartSeries[] => {
      const match = series.find((s) => s.labels['messageType'] === type && s.labels['direction'] === direction.value)
      return match ? [{ key: `t${type}`, name: typeName(type), color: colors[slot]!, points: match.points }] : []
    }),
  }))
}
