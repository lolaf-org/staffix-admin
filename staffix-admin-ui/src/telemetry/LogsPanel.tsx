import { useState } from 'react'
import { Alert, Checkbox, Flex, Input, Segmented, Select, Table, Tag, Tooltip, Typography } from 'antd'
import { ArrowDownOutlined, ArrowUpOutlined, InfoCircleOutlined } from '@ant-design/icons'
import type { Schemas } from '../api/client'
import { useLogs, type DecodeTarget, type LogType } from '../api/queries'
import { DecodedMessageModal } from './DecodedMessageModal'
import { formatDateTime, formatLogTime } from './format'
import { TIME_WINDOWS } from './timeWindows'

const TYPES: { value: LogType; label: string; icon: React.ReactNode }[] = [
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
  const [minutes, setMinutes] = useState(15)
  const [text, setText] = useState('')
  const [types, setTypes] = useState<LogType[]>([])
  const [limit, setLimit] = useState(500)
  const [decoding, setDecoding] = useState<DecodeTarget>()
  const logs = useLogs({ engineId, group, sessionId, minutes, text, types, limit }, engineId !== '')

  return (
    <Flex vertical gap={12}>
      <Flex wrap gap={8} align="center">
        <Input.Search
          placeholder="Text, e.g. 35=D or ORD-1234"
          allowClear
          onSearch={setText}
          style={{ width: 280, maxWidth: '100%' }}
        />
        <Checkbox.Group
          value={types}
          onChange={(values) => setTypes(values as LogType[])}
          options={TYPES.map((t) => ({ value: t.value, label: t.label }))}
        />
        <Segmented options={TIME_WINDOWS} value={minutes} onChange={setMinutes} />
        <Select
          value={limit}
          onChange={setLimit}
          options={[100, 500, 1000, 5000].map((n) => ({ value: n, label: `${n} lines` }))}
          style={{ width: 120 }}
          aria-label="Maximum lines"
        />
      </Flex>
      {logs.isError && <Alert type="error" title={logs.error.message} />}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Click a message to decode it with its session's dictionary.
      </Typography.Text>
      <Table<Schemas['LogEntry']>
        size="small"
        loading={logs.isFetching}
        dataSource={logs.data}
        rowKey={(entry) => `${entry.timestamp}-${entry.group}-${entry.sessionId}-${entry.line}`}
        pagination={{ pageSize: 50, showSizeChanger: false, size: 'small' }}
        onRow={(entry) =>
          decodable(entry)
            ? {
                onClick: () =>
                  setDecoding({ ref: { engineId, group: entry.group!, sessionId: entry.sessionId! }, message: entry.line, separator: '|' }),
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
              const known = TYPES.find((t) => t.value === type)
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
