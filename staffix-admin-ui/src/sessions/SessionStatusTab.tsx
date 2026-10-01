import { useState } from 'react'
import { Badge, Descriptions, Divider, Flex, InputNumber, Modal, Select, Table, Tag, Typography } from 'antd'
import { api, call, type Schemas } from '../api/client'
import { sessionPath, useEngineMutation, type SessionRef } from '../api/queries'
import { HEALTH_LABELS, HEALTH_STATUS } from './health'
import { RESET_MODES } from './resetModes'
import { WriteButton } from './WriteButton'

interface Props {
  sessionRef: SessionRef
  detail: Schemas['SessionDetail']
}

export function SessionStatusTab({ sessionRef, detail }: Props) {
  const { summary } = detail
  const logon = useEngineMutation(sessionRef.engineId, () => call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/logon', sessionPath(sessionRef))), 'Logon requested')
  const logout = useEngineMutation(sessionRef.engineId, () => call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/logout', sessionPath(sessionRef))), 'Logout requested')

  return (
    <Flex vertical gap={16}>
      <Descriptions
        size="small"
        column={1}
        bordered
        items={[
          { label: 'Status', children: <Badge status={HEALTH_STATUS[summary.health]} text={HEALTH_LABELS[summary.health]} /> },
          { label: 'Group', children: summary.group },
          { label: 'Session', children: summary.id },
          { label: 'Instance', children: `${summary.type.toLowerCase()} ${summary.instanceId}` },
          { label: 'Running config', children: summary.runningConfigId },
          { label: 'FIX session id', children: <Typography.Text copyable>{summary.fixSessionId}</Typography.Text> },
          { label: 'Connected', children: yesNo(summary.connected) },
          { label: 'Within session time', children: yesNo(summary.withinSessionTime) },
          {
            label: 'Desired state',
            children: summary.desiredState === 'LOGGED_OUT' ? <Tag>logged out by an operator</Tag> : summary.desiredState.toLowerCase().replace('_', ' '),
          },
          { label: 'Next incoming seq num', children: detail.incomingSeqNum },
          { label: 'Next outgoing seq num', children: detail.outgoingSeqNum },
        ]}
      />
      <Flex wrap gap={8}>
        <WriteButton type="primary" loading={logon.isPending} onConfirm={() => logon.mutate(undefined)}>
          Log on
        </WriteButton>
        <WriteButton danger loading={logout.isPending} onConfirm={() => logout.mutate(undefined)} confirm={`Log out ${summary.group}.${summary.id}?`}>
          Log out
        </WriteButton>
        <SeqNumsButton sessionRef={sessionRef} detail={detail} />
      </Flex>
      <ResetSection sessionRef={sessionRef} summary={summary} />
      {summary.type === 'INITIATOR' && detail.configs.length > 1 && <Configs sessionRef={sessionRef} detail={detail} />}
    </Flex>
  )
}

function ResetSection({ sessionRef, summary }: { sessionRef: SessionRef; summary: Schemas['SessionSummary'] }) {
  const modes = RESET_MODES.filter((m) => !m.initiatorOnly || summary.type === 'INITIATOR')
  const [mode, setMode] = useState(modes[0]!.mode)
  const reset = useEngineMutation(
    sessionRef.engineId,
    () => call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/reset', { ...sessionPath(sessionRef), body: { mode } })),
    'Reset requested',
  )
  const selected = RESET_MODES.find((m) => m.mode === mode)!

  return (
    <div>
      <Divider titlePlacement="start" plain>
        Sequence reset
      </Divider>
      <Flex wrap gap={8}>
        <Select value={mode} onChange={setMode} options={modes.map((m) => ({ value: m.mode, label: m.label }))} style={{ minWidth: 240 }} />
        <WriteButton danger loading={reset.isPending} onConfirm={() => reset.mutate(undefined)} confirm={`${selected.label} on ${summary.group}.${summary.id}?`}>
          Reset
        </WriteButton>
      </Flex>
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        {selected.description}
      </Typography.Paragraph>
    </div>
  )
}

function SeqNumsButton({ sessionRef, detail }: Props) {
  const [open, setOpen] = useState(false)
  const [incoming, setIncoming] = useState<number | null>(null)
  const [outgoing, setOutgoing] = useState<number | null>(null)
  const update = useEngineMutation(
    sessionRef.engineId,
    () => call(api.PUT('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/seqnums', { ...sessionPath(sessionRef), body: { incoming, outgoing } })),
    'Sequence numbers updated',
  )

  const show = () => {
    setIncoming(detail.incomingSeqNum)
    setOutgoing(detail.outgoingSeqNum)
    setOpen(true)
  }

  return (
    <>
      <WriteButton onConfirm={show}>Set seq nums</WriteButton>
      <Modal
        title={`Sequence numbers of ${detail.summary.runningConfigId}`}
        open={open}
        okText="Apply"
        confirmLoading={update.isPending}
        onCancel={() => setOpen(false)}
        onOk={() => update.mutate(undefined, { onSuccess: () => setOpen(false) })}
      >
        <Flex vertical gap={12}>
          <label>
            Next incoming
            <InputNumber min={1} value={incoming} onChange={setIncoming} style={{ width: '100%' }} />
          </label>
          <label>
            Next outgoing
            <InputNumber min={1} value={outgoing} onChange={setOutgoing} style={{ width: '100%' }} />
          </label>
        </Flex>
      </Modal>
    </>
  )
}

function Configs({ sessionRef, detail }: Props) {
  const activate = useEngineMutation(
    sessionRef.engineId,
    (configId: string) =>
      call(api.POST('/api/engines/{engineId}/groups/{group}/sessions/{sessionId}/activate', { ...sessionPath(sessionRef), body: { configId } })),
    'Switch requested',
  )

  return (
    <div>
      <Divider titlePlacement="start" plain>
        Configs
      </Divider>
      <Table<Schemas['SessionConfig']>
        size="small"
        pagination={false}
        rowKey="id"
        dataSource={detail.configs}
        columns={[
          {
            title: 'Config',
            dataIndex: 'id',
            render: (id: string, _, index) => (
              <>
                {id} <Tag>{index === 0 ? 'main' : 'backup'}</Tag>
              </>
            ),
          },
          { title: 'Addresses', dataIndex: 'connectAddresses', render: (addresses: string[]) => addresses.join(', ') },
          {
            title: '',
            align: 'right',
            render: (_, config) =>
              config.id === detail.summary.runningConfigId ? (
                <Tag color="processing">running</Tag>
              ) : (
                <WriteButton
                  size="small"
                  loading={activate.isPending}
                  onConfirm={() => activate.mutate(config.id)}
                  confirm={`Switch ${detail.summary.group}.${detail.summary.id} to ${config.id}? The running config is logged out first.`}
                >
                  Switch to
                </WriteButton>
              ),
          },
        ]}
      />
    </div>
  )
}

function yesNo(value: boolean) {
  return value ? 'yes' : 'no'
}
