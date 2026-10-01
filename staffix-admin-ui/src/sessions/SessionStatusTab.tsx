import { useState } from 'react'
import { Badge, Descriptions, Divider, Flex, InputNumber, Modal, Select, Table, Tag, Typography } from 'antd'
import { useNavigate, useLocation } from 'react-router'
import { api, call, type Schemas } from '../api/client'
import { useEngineMutation } from '../api/queries'
import { HEALTH_LABELS, HEALTH_STATUS } from './health'
import { RESET_MODES } from './resetModes'
import { WriteButton } from './WriteButton'

interface Props {
  instanceId: string
  detail: Schemas['SessionDetail']
}

export function SessionStatusTab({ instanceId, detail }: Props) {
  const { summary } = detail
  const sessionId = summary.id
  const path = { params: { path: { instanceId, sessionId } } }

  const logon = useEngineMutation(instanceId, () => call(api.POST('/api/engines/{instanceId}/sessions/{sessionId}/logon', path)), 'Logon requested')
  const logout = useEngineMutation(instanceId, () => call(api.POST('/api/engines/{instanceId}/sessions/{sessionId}/logout', path)), 'Logout requested')

  return (
    <Flex vertical gap={16}>
      <Descriptions
        size="small"
        column={1}
        bordered
        items={[
          { label: 'Status', children: <Badge status={HEALTH_STATUS[summary.health]} text={HEALTH_LABELS[summary.health]} /> },
          { label: 'FIX session id', children: <Typography.Text copyable>{summary.fixSessionId}</Typography.Text> },
          { label: 'Engine', children: instanceId },
          { label: 'Group', children: summary.group },
          { label: 'Type', children: summary.type.toLowerCase() },
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
        <WriteButton danger loading={logout.isPending} onConfirm={() => logout.mutate(undefined)} confirm={`Log out ${sessionId}?`}>
          Log out
        </WriteButton>
        <SeqNumsButton instanceId={instanceId} detail={detail} />
      </Flex>
      <ResetSection instanceId={instanceId} summary={summary} />
      {detail.initiators.map((initiator) => (
        <BackupTargets key={initiator.initiatorId} instanceId={instanceId} initiator={initiator} />
      ))}
    </Flex>
  )
}

function ResetSection({ instanceId, summary }: { instanceId: string; summary: Schemas['SessionSummary'] }) {
  const modes = RESET_MODES.filter((m) => !m.initiatorOnly || summary.type === 'INITIATOR')
  const [mode, setMode] = useState(modes[0]!.mode)
  const reset = useEngineMutation(
    instanceId,
    () =>
      call(
        api.POST('/api/engines/{instanceId}/sessions/{sessionId}/reset', {
          params: { path: { instanceId, sessionId: summary.id } },
          body: { mode },
        }),
      ),
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
        <WriteButton danger loading={reset.isPending} onConfirm={() => reset.mutate(undefined)} confirm={`${selected.label} on ${summary.id}?`}>
          Reset
        </WriteButton>
      </Flex>
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        {selected.description}
      </Typography.Paragraph>
    </div>
  )
}

function SeqNumsButton({ instanceId, detail }: Props) {
  const [open, setOpen] = useState(false)
  const [incoming, setIncoming] = useState<number | null>(null)
  const [outgoing, setOutgoing] = useState<number | null>(null)
  const sessionId = detail.summary.id
  const update = useEngineMutation(
    instanceId,
    () =>
      call(
        api.PUT('/api/engines/{instanceId}/sessions/{sessionId}/seqnums', {
          params: { path: { instanceId, sessionId } },
          body: { incoming, outgoing },
        }),
      ),
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
        title={`Sequence numbers of ${sessionId}`}
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

function BackupTargets({ instanceId, initiator }: { instanceId: string; initiator: Schemas['InitiatorTargets'] }) {
  const navigate = useNavigate()
  const location = useLocation()
  const activate = useEngineMutation(
    instanceId,
    (sessionId: string) =>
      call(
        api.POST('/api/engines/{instanceId}/sessions/{sessionId}/activate', {
          params: { path: { instanceId, sessionId } },
        }),
      ).then(() => sessionId),
    'Switch requested',
  )

  if (initiator.targets.length < 2) {
    return null
  }

  const switchTo = (sessionId: string) =>
    activate.mutate(sessionId, {
      onSuccess: () => navigate({ pathname: `/engines/${instanceId}/sessions/${sessionId}`, search: location.search }),
    })

  return (
    <div>
      <Divider titlePlacement="start" plain>
        Backup targets
      </Divider>
      <Table<Schemas['InitiatorTarget']>
        size="small"
        pagination={false}
        rowKey="sessionId"
        dataSource={initiator.targets}
        columns={[
          {
            title: 'Session',
            dataIndex: 'sessionId',
            render: (id: string, _, index) => (
              <>
                {id} {index === 0 ? <Tag>main</Tag> : <Tag>backup</Tag>}
              </>
            ),
          },
          { title: 'Addresses', dataIndex: 'connectAddresses', render: (addresses: string[]) => addresses.join(', ') },
          {
            title: '',
            align: 'right',
            render: (_, target) =>
              target.sessionId === initiator.activeSessionId ? (
                <Tag color="processing">active</Tag>
              ) : (
                <WriteButton size="small" loading={activate.isPending} onConfirm={() => switchTo(target.sessionId)} confirm={`Switch to ${target.sessionId}? The active session is logged out first.`}>
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
