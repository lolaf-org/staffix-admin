import { Alert, Button, Card, Collapse, Empty, Flex, Select, Skeleton, Space, Table, Tag, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { ApiError } from '../api/client'
import {
  type Connector,
  type ConnectorsConfig,
  useCanWrite,
  useConnectorEngines,
  useConnectorKinds,
  useConnectors,
  useSaveConnectors,
} from '../api/queries'
import { WriteButton } from '../sessions/WriteButton'
import { ConnectorModal } from './ConnectorModal'
import { EnginePanel } from './EnginePanel'
import { assignmentOptions, type Role, ROLE_LABELS, roleKey, roleOf } from './draft'

/**
 * Edits a draft of the whole connectors file, saved in one go so its version guards against a concurrent change.
 */
export function ConnectorsPage() {
  const stored = useConnectors()
  const kinds = useConnectorKinds()
  const engines = useConnectorEngines()
  const save = useSaveConnectors()
  const canWrite = useCanWrite()
  const [draft, setDraft] = useState<ConnectorsConfig>()
  const [editing, setEditing] = useState<{ connector?: Connector } | undefined>()

  if (stored.isPending || kinds.isPending) return <Skeleton active />
  if (stored.isError) return <Alert type="error" title={stored.error.message} />
  if (kinds.isError) return <Alert type="error" title={kinds.error.message} />

  const config = draft ?? stored.data
  const change = (next: ConnectorsConfig) => {
    save.reset()
    setDraft(next)
  }
  const putConnector = (connector: Connector) => {
    const exists = config.connectors.some((c) => c.id === connector.id)
    const connectors = exists ? config.connectors.map((c) => (c.id === connector.id ? connector : c)) : [...config.connectors, connector]
    change({ ...config, connectors })
    setEditing(undefined)
  }
  const removeConnector = (id: string) => change({ ...config, connectors: config.connectors.filter((c) => c.id !== id) })
  const setGlobalDefault = (role: Role, connectorId: string) =>
    change({ ...config, defaults: { ...config.defaults, [roleKey(role)]: connectorId || undefined } })
  const discard = () => {
    save.reset()
    setDraft(undefined)
  }
  const reload = async () => {
    discard()
    await stored.refetch()
  }

  const engineIds = [...new Set([...(engines.data ?? []).map((e) => e.engineId), ...Object.keys(config.engines)])].sort()

  return (
    <Flex vertical gap={16}>
      {draft && (
        <Alert
          type="info"
          title="Unsaved changes"
          action={
            <Space>
              <Button onClick={discard}>Discard</Button>
              <WriteButton type="primary" loading={save.isPending} onConfirm={() => save.mutate(draft, { onSuccess: () => setDraft(undefined) })}>
                Save
              </WriteButton>
            </Space>
          }
        />
      )}
      {save.isError && <SaveError error={save.error} onReload={reload} />}
      {!canWrite && <Alert type="warning" title="Read only: changing connectors requires the writer role" />}

      <Card
        size="small"
        title="Connectors"
        extra={
          <WriteButton icon={<PlusOutlined />} onConfirm={() => setEditing({})}>
            Add connector
          </WriteButton>
        }
      >
        <Table<Connector>
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={config.connectors}
          locale={{ emptyText: <Empty description="No connector: metrics and logs are not collected" /> }}
          columns={[
            { title: 'Name', render: (_, c) => <Typography.Link onClick={() => setEditing({ connector: c })}>{c.name}</Typography.Link> },
            { title: 'Id', dataIndex: 'id' },
            {
              title: 'Kind',
              render: (_, c) => {
                const role = roleOf(kinds.data, c.kind)
                return <>{c.kind} {role && <Tag>{ROLE_LABELS[role]}</Tag>}</>
              },
            },
            { title: 'URL', render: (_, c) => String(c.settings.url ?? '') },
            {
              title: '',
              width: 100,
              render: (_, c) => (
                <WriteButton size="small" danger confirm={`Remove ${c.name}? Engines using it fall back on their defaults.`} onConfirm={() => removeConnector(c.id)}>
                  Remove
                </WriteButton>
              ),
            },
          ]}
        />
      </Card>

      <Card size="small" title="Global defaults" extra={<Typography.Text type="secondary">For every emitter without its own connector</Typography.Text>}>
        <Flex wrap gap={16}>
          {(['METRICS', 'LOGS'] as Role[]).map((role) => (
            <label key={role}>
              <Typography.Text type="secondary">{ROLE_LABELS[role]}</Typography.Text>
              <Select
                value={config.defaults[roleKey(role)] ?? ''}
                onChange={(value) => setGlobalDefault(role, value)}
                options={assignmentOptions(config, kinds.data, role)}
                disabled={!canWrite}
                style={{ display: 'block', width: 320, maxWidth: '100%' }}
              />
            </label>
          ))}
        </Flex>
      </Card>

      <Card size="small" title="Engines">
        {engines.isError && <Alert type="warning" title={`Engines' emitters unavailable: ${engines.error.message}`} />}
        {engineIds.length === 0 ? (
          <Empty description="No engine has announced itself yet" />
        ) : (
          <Collapse
            items={engineIds.map((engineId) => {
              const emitters = engines.data?.find((e) => e.engineId === engineId)
              return {
                key: engineId,
                label: (
                  <Space>
                    {engineId}
                    {emitters && !emitters.announced && <Tag>not announced</Tag>}
                    {emitters?.announced && !emitters.reachable && <Tag color="warning">unreachable</Tag>}
                    {config.engines[engineId] && <Tag color="processing">own assignments</Tag>}
                  </Space>
                ),
                children: (
                  <EnginePanel engineId={engineId} emitters={emitters} config={config} kinds={kinds.data} disabled={!canWrite} onChange={change} />
                ),
              }
            })}
          />
        )}
      </Card>

      <ConnectorModal
        open={editing !== undefined}
        connector={editing?.connector}
        kinds={kinds.data}
        takenIds={config.connectors.map((c) => c.id)}
        onSave={putConnector}
        onClose={() => setEditing(undefined)}
      />
    </Flex>
  )
}

function SaveError({ error, onReload }: { error: Error; onReload: () => void }) {
  if (error instanceof ApiError && error.status === 409) {
    return (
      <Alert
        type="error"
        title="Someone else changed the connectors since you loaded them"
        description="Reload to see their change; your unsaved changes are lost."
        action={<Button onClick={onReload}>Reload</Button>}
      />
    )
  }
  const problems = error instanceof ApiError ? error.problems : []
  return (
    <Alert
      type="error"
      title={problems.length > 0 ? 'Not saved' : error.message}
      description={problems.length > 0 && <ul style={{ margin: 0, paddingLeft: 20 }}>{problems.map((p) => <li key={p}>{p}</li>)}</ul>}
    />
  )
}
