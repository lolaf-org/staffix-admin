import { useState } from 'react'
import { Alert, Checkbox, Flex, Input, Select, Table, Tag, Tooltip, Typography } from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined, InfoCircleOutlined } from '@ant-design/icons'
import { isNotCollected, type Schemas } from '../api/client'
import { NotCollectedNotice } from './NotCollectedNotice'
import { useLogs, useMessageTypes, type DecodeTarget, type LogType } from '../api/queries'
import { DecodedMessageModal } from './DecodedMessageModal'
import { formatDateTime, formatLogTime } from './format'
import { TimeRangeSelector, type Period } from './TimeRangeSelector'

export const LOG_TYPES: { value: LogType; label: string; icon: React.ReactNode }[] = [
  { value: 'IN', label: 'in', icon: <ArrowDownOutlined /> },
  { value: 'OUT', label: 'out', icon: <ArrowUpOutlined /> },
  { value: 'EVENT', label: 'event', icon: <InfoCircleOutlined /> },
]

interface Props {
  engineId: string
  group?: string
  /**
   * A config id of the group.
   */
  sessionId?: string
}

export function LogsPanel({ engineId, group, sessionId }: Props) {
  const [period, setPeriod] = useState<Period>(15)
  const [live, setLive] = useState(false)
  const [text, setText] = useState('')
  const [types, setTypes] = useState<LogType[]>([])
  const [msgTypes, setMsgTypes] = useState<string[]>([])
  const messageTypes = useMessageTypes({ engineId, group, sessionId })
  const [limit, setLimit] = useState(100)
  const [decoding, setDecoding] = useState<DecodeTarget>()
  const logs = useLogs({ engineId, group, sessionId, period, text, types, msgTypes, limit }, engineId !== '', live)

  return (
    <Flex vertical gap={12}>
      <Flex wrap gap={8} align="center">
        <Input.Search
          placeholder="Text, e.g. ORD-1234"
          allowClear
          onSearch={setText}
          style={{ width: 280, maxWidth: '100%' }}
        />
        <Select
          mode="multiple"
          allowClear
          showSearch
          placeholder="Any message type"
          value={msgTypes}
          onChange={setMsgTypes}
          loading={messageTypes.isPending}
          maxTagCount="responsive"
          optionFilterProp="label"
          options={messageTypes.data?.map((m) => ({ value: m.msgType, label: `${m.name} (${m.msgType})` }))}
          style={{ width: 300, maxWidth: '100%' }}
          aria-label="Message types"
        />
        <Checkbox.Group
          value={types}
          onChange={(values) => setTypes(values as LogType[])}
          options={LOG_TYPES.map((t) => ({ value: t.value, label: t.label }))}
        />
        <TimeRangeSelector value={period} onChange={setPeriod} live={live} onLiveChange={setLive} />
        <Select
          value={limit}
          onChange={setLimit}
          options={[50, 100, 200, 500].map((n) => ({ value: n, label: `${n} lines` }))}
          style={{ width: 120 }}
          aria-label="Maximum lines"
        />
      </Flex>
      {logs.isError && <Alert type={isNotCollected(logs.error) ? 'info' : 'error'} title={logs.error.message} />}
      {logs.isSuccess && <NotCollectedNotice what="logs" sessions={logs.data.notCollected} />}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Click a message to decode it with its session's dictionary.
      </Typography.Text>
      <Table<Schemas['LogEntry']>
        size="small"
        // a live refresh keeps the entries shown rather than flashing the table
        loading={logs.isPending || (logs.isFetching && !live)}
        dataSource={logs.data?.entries}
        rowKey={(entry) => `${entry.timestamp}-${entry.group}-${entry.sessionId}-${entry.line}`}
        pagination={{ pageSize: 50, showSizeChanger: false, size: 'small' }}
        onRow={(entry) =>
          decodable(entry)
            ? {
                onClick: () =>
                  setDecoding({
                    ref: { engineId, group: entry.group!, sessionId: entry.sessionId! },
                    message: entry.line,
                    separator: '|',
                    timestamp: entry.timestamp,
                  }),
                style: { cursor: 'pointer' },
              }
            : {}
        }
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
          ...(sessionId
            ? []
            : [
                {
                  title: 'Session',
                  key: 'session',
                  width: 160,
                  render: (_: unknown, entry: Schemas['LogEntry']) => (group ? entry.sessionId : `${entry.group}.${entry.sessionId}`),
                },
              ]),
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
            title: 'Line',
            dataIndex: 'line',
            render: (line: string) => (
              <Typography.Text style={{ fontFamily: 'monospace', fontSize: 12, wordBreak: 'break-all' }}>{line}</Typography.Text>
            ),
          },
        ]}
      />
      <DecodedMessageModal target={decoding} onClose={() => setDecoding(undefined)} />
    </Flex>
  )
}

function decodable(entry: Schemas['LogEntry']): boolean {
  return entry.type !== 'EVENT' && entry.group != null && entry.sessionId != null
}
