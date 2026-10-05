import { useEffect, useState } from 'react'
import { Alert, Empty, Flex, Segmented, Select, Skeleton, Typography } from 'antd'
import { AreaChartOutlined, TableOutlined } from '@ant-design/icons'
import {
  useMessageTypes,
  useMeters,
  useMetrics,
  type FixedPeriod,
  type Meter,
  type MetricRequest,
  type MetricScope,
} from '../api/queries'
import { isNotCollected, type Schemas } from '../api/client'
import { NotCollectedNotice } from './NotCollectedNotice'
import { formatRateValue, formatSeconds } from './format'
import { MetricChart, SeriesLegend, niceTicks, type ChartSeries } from './MetricChart'
import { MetricTable } from './MetricTable'
import { messageTypeName, plottedMessageTypes, rankedMessageTypes, SERIES_COUNT, seriesColors } from './series'
import { useThemeMode } from '../theme/ThemeMode'
import { TimeRangeSelector, type Period } from './TimeRangeSelector'
import { durationSeconds, parseDuration } from '../settings/duration'

const MESSAGES = 'messages'
const MESSAGE_TYPE = 'fix.msg.type'
const DIRECTION = 'fix.msg.dir'
const READ = 'messages.read.latency'
const WRITE = 'messages.write.latency'
/**
 * staffix's own meters, in the order operators read them, with short labels; any other meter is the application's.
 */
const BUILT_IN: Record<string, { label: string; title: string }> = {
  [READ]: { label: 'Read', title: 'Read latency' },
  [WRITE]: { label: 'Write', title: 'Write latency' },
  'messages.decoding.latency': { label: 'Decoding', title: 'Decoding latency' },
  'messages.encoding.latency': { label: 'Encoding', title: 'Encoding latency' },
  'session.rtt': { label: 'RTT', title: 'Round-trip time' },
  'session.clock.offset': { label: 'Clock offset', title: 'Clock offset, remote minus local' },
}
/**
 * The logon state is what the session status already shows; charted, it says nothing more.
 */
const NOT_CHARTED = ['session.logon.state']
const MESSAGE_RATE_DESCRIPTION =
  'FIX messages per second, per message type: received ones counted by the read timer, sent ones by the write timer.'

const QUANTILES = [0.5, 0.9, 0.99, 0.999]
const MEAN = 'mean'
const RATE = 'rate'
const AUTO = 'auto'
const RATE_WINDOWS = ['PT1S', 'PT2S', 'PT5S', 'PT10S', 'PT30S', 'PT1M', 'PT5M']
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
  const meters = useMeters(scope).data
  const [selected, setSelected] = useState(MESSAGES)
  const [statistic, setStatistic] = useState(String(0.99))
  const [selectedPeriod, setSelectedPeriod] = useState<Period>(60)
  const [rateWindow, setRateWindow] = useState(AUTO)
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const [pickedTypes, setPickedTypes] = useState<string[]>([])
  const sessionScoped = scope.sessionId !== undefined
  const ordered = orderedMeters(meters ?? [])
  const hasMessageRate = ordered.some((m) => m.name === READ || m.name === WRITE)
  const meter = ordered.find((m) => m.name === selected)
  useEffect(() => {
    if (meters === undefined) return
    if (selected === MESSAGES ? !hasMessageRate : meter === undefined) {
      const first = hasMessageRate ? MESSAGES : ordered[0]?.name
      if (first) choose(first)
    }
  }, [meters, selected])
  const request = requestOf(selected, meter, statistic)
  const metrics = useMetrics(scope, request, period ?? selectedPeriod, rateWindow === AUTO ? undefined : rateWindow)
  const minRateWindow = metrics.data?.minRateWindow ? durationSeconds(metrics.data.minRateWindow) ?? 0 : 0
  const colors = seriesColors(useThemeMode().dark)
  const dictionaryTypes = useMessageTypes(scope).data
  const typeName = (code: string) => messageTypeName(code, dictionaryTypes?.find((t) => t.msgType === code)?.name)
  const typesWithData = metrics.data?.series.flatMap((s) => (s.labels[MESSAGE_TYPE] ? [s.labels[MESSAGE_TYPE]] : [])) ?? []
  const plottedTypes = plottedMessageTypes(typesWithData, pickedTypes)
  const format = formatOf(request, meter)
  const title = titleOf(selected, meter, statistic)

  function choose(name: string) {
    setSelected(name)
    setStatistic(defaultStatistic(ordered.find((m) => m.name === name)))
  }

  if (meters !== undefined && ordered.length === 0) {
    return <Empty description="No session of this scope publishes metrics: none runs, or none has a monitoring plugin" />
  }

  return (
    <Flex vertical gap={16}>
      <Segmented<string>
        options={[
          ...(hasMessageRate ? [{ value: MESSAGES, label: 'Rate', title: MESSAGE_RATE_DESCRIPTION }] : []),
          ...ordered.map((m) => ({
            value: m.name,
            label: BUILT_IN[m.name]?.label ?? readableName(m.name),
            title: BUILT_IN[m.name] ? m.description : `${m.name}: ${m.description}`,
          })),
        ]}
        value={selected}
        onChange={choose}
      />
      <Flex wrap gap={8}>
        {meter?.type === 'TIMER' && (
          <Select
            value={statistic}
            onChange={setStatistic}
            options={[
              ...(meter.percentiles ? QUANTILES.map((q) => ({ value: String(q), label: `p${q * 100}` })) : []),
              { value: MEAN, label: 'mean' },
              { value: RATE, label: 'per second' },
            ]}
            style={{ width: 120 }}
            aria-label="Statistic"
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
        {request?.kind !== 'meter' || request.statistic !== 'VALUE' ? (
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
        ) : null}
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

function orderedMeters(meters: Meter[]): Meter[] {
  const builtIn = Object.keys(BUILT_IN)
  const rank = (m: Meter) => (builtIn.includes(m.name) ? builtIn.indexOf(m.name) : builtIn.length)
  return meters.filter((m) => !NOT_CHARTED.includes(m.name)).sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name))
}

function readableName(id: string): string {
  const words = id.split(/[._-]+/).filter((word) => word !== '').join(' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * The RTT is already smoothed by the engine, so its mean reads best; other timers their p99 when they have one.
 */
function defaultStatistic(meter: Meter | undefined): string {
  return meter?.type !== 'TIMER' || meter.name === 'session.rtt' || !meter.percentiles ? MEAN : String(0.99)
}

function requestOf(selected: string, meter: Meter | undefined, statistic: string): MetricRequest | undefined {
  if (selected === MESSAGES) return { kind: 'messages' }
  if (!meter) return undefined
  if (meter.type === 'GAUGE') return { kind: 'meter', meter: meter.name, statistic: 'VALUE', splitBy: meter.tagKeys, gaugeUnit: meter.unit }
  if (statistic === MEAN || statistic === RATE) {
    return { kind: 'meter', meter: meter.name, statistic: statistic === MEAN ? 'MEAN' : 'RATE', splitBy: meter.tagKeys }
  }
  return { kind: 'meter', meter: meter.name, statistic: 'QUANTILE', quantile: Number(statistic), splitBy: meter.tagKeys }
}

function formatOf(request: MetricRequest | undefined, meter: Meter | undefined): (value: number) => string {
  if (request?.kind !== 'meter' || request.statistic === 'RATE') return formatRateValue
  if (request.statistic === 'VALUE' && !meter?.unit) return (value) => String(Number(value.toFixed(3)))
  return formatSeconds
}

function titleOf(selected: string, meter: Meter | undefined, statistic: string): string {
  if (selected === MESSAGES) return 'Messages per second'
  const name = BUILT_IN[selected]?.title ?? readableName(selected)
  if (meter?.type !== 'TIMER') return name
  return statistic === MEAN ? `${name}, mean` : statistic === RATE ? `${name}, per second` : `${name}, p${Number(statistic) * 100}`
}

function toFacets(series: Schemas['MetricSeries'][], title: string, colors: string[], types: string[],
                  typeName: (code: string) => string): Facet[] {
  if (!series.some((s) => s.labels[MESSAGE_TYPE])) {
    return [{
      title,
      series: series.slice(0, SERIES_COUNT).map((s, slot) => ({
        key: `s${slot}`,
        name: Object.values(s.labels).join(', ') || title,
        color: colors[slot]!,
        points: s.points,
      })),
    }]
  }
  const directions = DIRECTIONS.filter((direction) => series.some((s) => s.labels[DIRECTION] === direction.value))
  return directions.map((direction) => ({
    title: `${title}, ${direction.title}`,
    series: types.flatMap((type, slot): ChartSeries[] => {
      const match = series.find((s) => s.labels[MESSAGE_TYPE] === type && s.labels[DIRECTION] === direction.value)
      return match ? [{ key: `t${type}`, name: typeName(type), color: colors[slot]!, points: match.points }] : []
    }),
  }))
}
