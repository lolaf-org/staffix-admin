import { useState } from 'react'
import { Alert, Card, Empty, Flex, Input, Select, Table, Tag, Tooltip, Typography } from 'antd'
import { useQueries } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { messageTypesQuery, useEngines, type DecodeTarget, type LogType } from '../api/queries'
import { DecodedMessageModal } from '../telemetry/DecodedMessageModal'
import { formatDateTime, formatLogTime } from '../telemetry/format'
import { LOG_TYPES } from '../telemetry/LogsPanel'
import { TimeRangeSelector, type Period } from '../telemetry/TimeRangeSelector'
import { fieldsOf, KEY_FIELDS, LIMIT, MAX_IDS, MAX_ROUNDS, separatorOf, useTrace, type TraceEntry } from '../trace/trace'

const EVERY_ENGINE = ''
const DEFAULT_MINUTES = 1440

/**
 * The id, engine and period live in the URL, so a trace can be shared and a decoded message can link to one.
 */
export function TracePage() {
  const engines = useEngines()
  const [params, setParams] = useSearchParams()
  const id = params.get('id') ?? ''
  const engine = params.get('engine') ?? EVERY_ENGINE
  const period = periodOf(params)
  const engineIds = engine === EVERY_ENGINE ? (engines.data?.map((e) => e.engineId) ?? []) : [engine]
  const trace = useTrace(id, engineIds, period)
  const [decoding, setDecoding] = useState<DecodeTarget>()
  const messageTypes = useMessageTypeNames(trace.data?.entries ?? [])

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([name, value]) => (value === undefined || value === '' ? next.delete(name) : next.set(name, value)))
    setParams(next, { replace: true })
  }
  const setPeriod = (picked: Period) =>
    update(typeof picked === 'number' ? { minutes: String(picked), from: undefined, to: undefined } : { minutes: undefined, ...picked })

  return (
    <Card size="small" title="Order trace">
      <Flex vertical gap={12}>
        <Flex wrap gap={8} align="center">
          <Input.Search
            key={id}
            placeholder="ClOrdID, OrderID, QuoteReqID..."
            defaultValue={id}
            allowClear
            enterButton="Trace"
            loading={trace.isFetching}
            onSearch={(value) => (value.trim() === id ? trace.refetch() : update({ id: value.trim() }))}
            style={{ width: 360, maxWidth: '100%' }}
          />
          <Select
            value={engine}
            onChange={(value) => update({ engine: value })}
            options={[
              { value: EVERY_ENGINE, label: 'Every engine' },
              ...(engines.data?.map((e) => ({ value: e.engineId, label: e.engineId })) ?? []),
            ]}
            style={{ width: 200 }}
            aria-label="Engine"
          />
          <TimeRangeSelector value={period} onChange={setPeriod} />
        </Flex>
        {id === '' ? (
          <Empty description="Enter an id to follow its order or quote across sessions and engines" />
        ) : (
          <TraceResult
            trace={trace}
            id={id}
            messageTypes={messageTypes}
            onFollow={(value) => update({ id: value })}
            onDecode={(entry) =>
              setDecoding({
                ref: { engineId: entry.engineId, group: entry.group!, sessionId: entry.sessionId! },
                message: entry.line,
                separator: separatorOf(entry.line),
                timestamp: entry.timestamp,
              })
            }
          />
        )}
      </Flex>
      <DecodedMessageModal target={decoding} onClose={() => setDecoding(undefined)} />
    </Card>
  )
}

interface ResultProps {
  trace: ReturnType<typeof useTrace>
  id: string
  messageTypes: Map<string, Map<string, string>>
  onFollow: (id: string) => void
  onDecode: (entry: TraceEntry) => void
}

function TraceResult({ trace, id, messageTypes, onFollow, onDecode }: ResultProps) {
  if (trace.isError) return <Alert type="error" title={trace.error.message} />
  const ids = trace.data?.ids ?? [id]
  return (
    <Flex vertical gap={12}>
      <Flex wrap gap={4} align="center">
        <Typography.Text type="secondary">Ids followed:</Typography.Text>
        {ids.map((value) => (
          <Tooltip key={value} title={value === id ? 'The id asked' : 'Trace from this id'}>
            <Tag color={value === id ? 'gold' : 'blue'} style={{ cursor: 'pointer' }} onClick={() => onFollow(value)}>
              {value}
            </Tag>
          </Tooltip>
        ))}
      </Flex>
      {trace.data?.truncated && (
        <Alert
          type="warning"
          showIcon
          title="The trace may be incomplete"
          description={`It stops after ${MAX_ROUNDS} rounds of linked ids, ${MAX_IDS} ids, or ${LIMIT} messages per engine and round: narrow the period.`}
        />
      )}
      {trace.data && trace.data.notCollected.length > 0 && (
        <Alert type="info" showIcon title="Logs not collected" description={trace.data.notCollected.join(', ')} />
      )}
      {trace.data?.failures.map((failure) => <Alert key={failure} type="error" title={failure} />)}
      <Table<TraceEntry>
        size="small"
        loading={trace.isFetching}
        dataSource={trace.data?.entries}
        rowKey={(entry) => `${entry.engineId}-${entry.timestamp}-${entry.group}-${entry.sessionId}-${entry.line}`}
        pagination={{ pageSize: 100, showSizeChanger: false, size: 'small', hideOnSinglePage: true }}
        onRow={(entry) => (entry.type !== 'EVENT' && entry.group != null && entry.sessionId != null ? { onClick: () => onDecode(entry), style: { cursor: 'pointer' } } : {})}
        columns={[
          {
            title: 'Time',
            dataIndex: 'timestamp',
            width: 110,
            render: (timestamp: string) => (
              <Tooltip title={formatDateTime(Date.parse(timestamp))}>
                <Typography.Text style={{ fontFamily: 'monospace', fontSize: 12 }}>{formatLogTime(timestamp)}</Typography.Text>
              </Tooltip>
            ),
          },
          { title: 'Engine', dataIndex: 'engineId', width: 130 },
          {
            title: 'Session',
            key: 'session',
            width: 170,
            render: (_: unknown, entry: TraceEntry) => `${entry.group}.${entry.sessionId}`,
          },
          {
            title: 'Type',
            dataIndex: 'type',
            width: 80,
            render: (type: LogType | null) => {
              const known = LOG_TYPES.find((t) => t.value === type)
              return known ? <Tag icon={known.icon}>{known.label}</Tag> : null
            },
          },
          {
            title: 'Message',
            key: 'message',
            width: 190,
            render: (_: unknown, entry: TraceEntry) => {
              const msgType = fieldsOf(entry.line).get('35')
              if (msgType === undefined) return null
              const name = messageTypes.get(entry.engineId)?.get(msgType)
              return name ? `${name} (${msgType})` : msgType
            },
          },
          {
            title: 'Fields',
            key: 'fields',
            render: (_: unknown, entry: TraceEntry) => <KeyFields line={entry.line} id={id} ids={ids} />,
          },
        ]}
      />
    </Flex>
  )
}

function KeyFields({ line, id, ids }: { line: string; id: string; ids: string[] }) {
  const fields = fieldsOf(line)
  const present = KEY_FIELDS.filter((field) => fields.has(field.tag))
  if (present.length === 0) {
    return <Typography.Text style={{ fontFamily: 'monospace', fontSize: 12, wordBreak: 'break-all' }}>{line}</Typography.Text>
  }
  return (
    <Flex wrap gap={4}>
      {present.map(({ tag, name }) => {
        const value = fields.get(tag)!
        return (
          <Tag key={tag} color={value === id ? 'gold' : ids.includes(value) ? 'blue' : undefined} style={{ marginInlineEnd: 0 }}>
            {name}={value}
          </Tag>
        )
      })}
    </Flex>
  )
}

/**
 * Message type names by engine, from the dictionaries of the engines the trace went through.
 */
function useMessageTypeNames(entries: TraceEntry[]): Map<string, Map<string, string>> {
  const engineIds = [...new Set(entries.map((entry) => entry.engineId))]
  const results = useQueries({ queries: engineIds.map((engineId) => messageTypesQuery({ engineId })) })
  return new Map(engineIds.map((engineId, i) => [engineId, new Map(results[i]?.data?.map((m) => [m.msgType, m.name]) ?? [])]))
}

function periodOf(params: URLSearchParams): Period {
  const from = params.get('from')
  const to = params.get('to')
  if (from && to) return { from, to }
  const minutes = Number(params.get('minutes'))
  return minutes > 0 ? minutes : DEFAULT_MINUTES
}
