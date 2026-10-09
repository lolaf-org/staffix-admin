import { Alert, Button, Flex, Modal, Segmented, Skeleton, Table, Tag, Typography, theme } from 'antd'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import type { Schemas } from '../api/client'
import { useDecodedMessage, useGroups, useTraceSettings, type DecodeTarget, type FixedPeriod, type SessionRef } from '../api/queries'
import { MetricsPanel } from './MetricsPanel'
import { sessionRoute, traceRoute } from './routes'

type DecodedField = Schemas['DecodedField']

const SECTION_LABELS: Record<DecodedField['section'], string> = { HEADER: 'header', BODY: 'body', TRAILER: 'trailer' }
const METRICS_AROUND_MS = 60_000

/**
 * A raw FIX message, each tag named with the dictionary of the session config it went through, and for a logged one
 * the session's metrics around it.
 */
export function DecodedMessageModal({ target, onClose }: { target: DecodeTarget | undefined; onClose: () => void }) {
  const decoded = useDecodedMessage(target)
  const [view, setView] = useState<'decoded' | 'raw' | 'metrics'>('decoded')
  const loggedAt = target?.timestamp === undefined ? undefined : Date.parse(target.timestamp)
  const { token } = theme.useToken()

  return (
    <Modal
      open={target !== undefined}
      onCancel={onClose}
      afterClose={() => setView('decoded')}
      footer={null}
      width={960}
      title={
        decoded.data ? (
          <Flex gap={8} align="baseline" wrap>
            <span>{decoded.data.msgTypeName ?? 'Unknown message type'}</span>
            {decoded.data.msgType && <Tag style={{ fontFamily: token.fontFamilyCode }}>35={decoded.data.msgType}</Tag>}
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              {target?.ref.group}.{target?.ref.sessionId}, dictionary {decoded.data.dictionaryIds.join(' + ')}
            </Typography.Text>
            {target && <OpenSessionButton configRef={target.ref} onOpen={onClose} />}
          </Flex>
        ) : (
          'Decoding'
        )
      }
    >
      {decoded.isPending && <Skeleton active />}
      {decoded.isError && <Alert type="error" title={decoded.error.message} />}
      {decoded.isSuccess && (
        <Flex vertical gap={12}>
          <Segmented
            value={view}
            onChange={setView}
            options={[
              { value: 'decoded', label: 'Decoded' },
              { value: 'raw', label: 'Raw' },
              ...(loggedAt === undefined ? [] : [{ value: 'metrics' as const, label: 'Metrics' }]),
            ]}
            style={{ alignSelf: 'flex-start' }}
          />
          {view === 'metrics' && target && loggedAt !== undefined ? (
            <MetricsPanel scope={target.ref} period={periodAround(loggedAt)} marker={loggedAt} chartHeight={180} />
          ) : view === 'raw' ? (
            <Typography.Paragraph copyable={{ text: target?.message }} style={{ margin: 0 }}>
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: 0 }}>{target?.message}</pre>
            </Typography.Paragraph>
          ) : (
            <FieldsTable fields={decoded.data.fields} onTrace={onClose} />
          )}
        </Flex>
      )}
    </Modal>
  )
}

/**
 * Hidden where the session's panel is already open, as from its own Logs or Send message tab.
 */
function OpenSessionButton({ configRef, onOpen }: { configRef: SessionRef; onOpen: () => void }) {
  const groups = useGroups(configRef.engineId)
  const navigate = useNavigate()
  const location = useLocation()
  const session = groups.data
    ?.find((group) => group.group === configRef.group)
    ?.sessions.find((candidate) => candidate.configIds.includes(configRef.sessionId))
  if (!session) {
    return null
  }
  const route = sessionRoute({ ...configRef, sessionId: session.id })
  if (location.pathname === route) {
    return null
  }
  return (
    <Button
      size="small"
      type="link"
      onClick={() => {
        onOpen()
        navigate(route)
      }}
    >
      Open session
    </Button>
  )
}

function periodAround(epochMillis: number): FixedPeriod {
  return {
    from: new Date(epochMillis - METRICS_AROUND_MS).toISOString(),
    to: new Date(epochMillis + METRICS_AROUND_MS).toISOString(),
  }
}

function FieldsTable({ fields, onTrace }: { fields: DecodedField[]; onTrace: () => void }) {
  const linkingTags = useTraceSettings().data?.linkingTags ?? []
  const { token } = theme.useToken()
  const mono = { fontFamily: token.fontFamilyCode, fontSize: 12 }
  const rows = fields.map((field, index) => ({ ...field, key: index, firstOfSection: index === 0 || fields[index - 1]!.section !== field.section }))

  return (
    <Table
      size="small"
      pagination={false}
      dataSource={rows}
      scroll={{ y: '60vh' }}
      columns={[
        {
          title: '',
          dataIndex: 'section',
          width: 70,
          render: (section: DecodedField['section'], row) =>
            row.firstOfSection ? <Typography.Text type="secondary" style={{ fontSize: 12 }}>{SECTION_LABELS[section]}</Typography.Text> : null,
        },
        { title: 'Tag', dataIndex: 'tag', width: 70, render: (tag: number) => <span style={mono}>{tag}</span> },
        {
          title: 'Field',
          dataIndex: 'name',
          render: (name: string | null, row) => (
            <div
              style={{
                paddingLeft: row.depth * 18,
                borderLeft: row.depth > 0 ? `2px solid ${token.colorBorderSecondary}` : undefined,
                marginLeft: row.depth > 0 ? (row.depth - 1) * 18 : 0,
              }}
            >
              {row.groupStart && <span aria-label="New group entry" style={{ color: token.colorTextTertiary, marginRight: 4 }}>{'▸'}</span>}
              {name ?? <Tag color="warning">not in dictionary</Tag>}
            </div>
          ),
        },
        {
          title: 'Value',
          dataIndex: 'value',
          render: (value: string, row) =>
            linkingTags.includes(row.tag) ? (
              <Link to={traceRoute(value)} onClick={onTrace} title="Trace this id across sessions" style={{ ...mono, wordBreak: 'break-all' }}>
                {value}
              </Link>
            ) : (
              <span style={{ ...mono, wordBreak: 'break-all' }}>{row.meaning ? `${row.meaning}(${value})` : value}</span>
            ),
        },
      ]}
    />
  )
}
