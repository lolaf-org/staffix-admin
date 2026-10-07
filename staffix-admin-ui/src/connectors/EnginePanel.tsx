import { Flex, Select, Table, Tag, Tooltip, Typography } from 'antd'
import type { Schemas } from '../api/client'
import type { ConnectorKind, ConsoleConfig } from '../api/queries'
import { assignmentOptions, connectorName, engineDefault, engineMapping, type Role, roleKey, withEngineMapping } from './draft'

type EngineEmitters = Schemas['EngineEmitters']

interface Props {
  engineId: string
  emitters?: EngineEmitters
  config: ConsoleConfig
  kinds: ConnectorKind[]
  disabled: boolean
  onChange: (config: ConsoleConfig) => void
}

/**
 * An engine's own defaults, then each of its emitters with the sessions using it: an emitter without its own
 * connector reads from the engine's default, else the global one.
 */
export function EnginePanel({ engineId, emitters, config, kinds, disabled, onChange }: Props) {
  const mapping = engineMapping(config, engineId)
  const globalDefault = (role: Role) => `${connectorName(config, config.defaults[roleKey(role)])} (global default)`

  const setDefault = (role: Role, connectorId: string) =>
    onChange(withEngineMapping(config, engineId, {
      ...mapping,
      defaults: { ...mapping.defaults, [roleKey(role)]: connectorId || undefined },
    }))
  const setEmitter = (field: 'loggers' | 'monitors', instanceId: string, connectorId: string) => {
    const assigned = { ...mapping[field] }
    if (connectorId) {
      assigned[instanceId] = connectorId
    } else {
      delete assigned[instanceId]
    }
    onChange(withEngineMapping(config, engineId, { ...mapping, [field]: assigned }))
  }

  return (
    <Flex vertical gap={16}>
      <Flex wrap gap={16}>
        {(['METRICS', 'LOGS'] as Role[]).map((role) => (
          <label key={role}>
            <Typography.Text type="secondary">Engine default for {role === 'METRICS' ? 'metrics' : 'logs'}</Typography.Text>
            <Select
              value={mapping.defaults[roleKey(role)] ?? ''}
              onChange={(value) => setDefault(role, value)}
              options={assignmentOptions(config, kinds, role, globalDefault(role))}
              disabled={disabled}
              style={{ display: 'block', width: 320, maxWidth: '100%' }}
            />
          </label>
        ))}
      </Flex>
      <EmitterTable
        title="Messages loggers (logs)"
        used={emitters?.loggers ?? []}
        assigned={mapping.loggers}
        options={assignmentOptions(config, kinds, 'LOGS', engineDefault(config, engineId, 'LOGS'))}
        disabled={disabled}
        onChange={(instanceId, connectorId) => setEmitter('loggers', instanceId, connectorId)}
      />
      <EmitterTable
        title="Monitoring plugins (metrics)"
        used={emitters?.monitors ?? []}
        assigned={mapping.monitors}
        options={assignmentOptions(config, kinds, 'METRICS', engineDefault(config, engineId, 'METRICS'))}
        disabled={disabled}
        onChange={(instanceId, connectorId) => setEmitter('monitors', instanceId, connectorId)}
      />
      {(emitters?.unmonitoredSessions.length ?? 0) > 0 && (
        <Typography.Text type="secondary">
          Not monitored, so no metrics: {emitters!.unmonitoredSessions.join(', ')}
        </Typography.Text>
      )}
    </Flex>
  )
}

type EmitterRow = Pick<Schemas['Emitter'], 'instanceId' | 'sessions'> & Partial<Pick<Schemas['Emitter'], 'types' | 'otlpEndpoint' | 'exports'>>

function typeChain(types: string[]): string {
  return types.map((type) => type.replace(/Settings$/, '')).join(' → ')
}

function EmitterTable({ title, used, assigned, options, disabled, onChange }: {
  title: string
  used: EmitterRow[]
  assigned: Record<string, string>
  options: { value: string; label: string }[]
  disabled: boolean
  onChange: (instanceId: string, connectorId: string) => void
}) {
  const rows: EmitterRow[] = [
    ...used,
    ...Object.keys(assigned).filter((id) => !used.some((u) => u.instanceId === id)).map((instanceId) => ({ instanceId, sessions: [] })),
  ]
  return (
    <Table<EmitterRow>
      size="small"
      title={() => title}
      rowKey="instanceId"
      pagination={false}
      dataSource={rows}
      locale={{ emptyText: 'None reported' }}
      columns={[
        { title: 'Instance', dataIndex: 'instanceId', width: '20%' },
        {
          title: 'Exports',
          render: (_, row) => (
            <Flex vertical>
              {row.types && row.types.length > 0 ? <Typography.Text>{typeChain(row.types)}</Typography.Text> : <Typography.Text type="secondary">unknown</Typography.Text>}
              {row.otlpEndpoint && <Typography.Text type="secondary" style={{ fontSize: 12 }}>OTLP to {row.otlpEndpoint}</Typography.Text>}
              {row.exports === false && (
                <Tooltip title="Its sessions' logs reach no backend, whatever connector is assigned">
                  <Tag color="warning" style={{ width: 'fit-content' }}>exports nothing</Tag>
                </Tooltip>
              )}
            </Flex>
          ),
        },
        {
          title: 'Sessions',
          render: (_, row) =>
            row.sessions.length === 0 ? (
              <Tooltip title="No running session uses it">
                <Tag>unused</Tag>
              </Tooltip>
            ) : (
              <Tooltip title={row.sessions.join(', ')}>
                <Tag>{row.sessions.length === 1 ? row.sessions[0] : `${row.sessions.length} sessions`}</Tag>
              </Tooltip>
            ),
        },
        {
          title: 'Connector',
          width: 340,
          render: (_, row) => (
            <Select
              value={assigned[row.instanceId] ?? ''}
              onChange={(value) => onChange(row.instanceId, value)}
              options={options}
              disabled={disabled}
              style={{ width: '100%' }}
            />
          ),
        },
      ]}
    />
  )
}
