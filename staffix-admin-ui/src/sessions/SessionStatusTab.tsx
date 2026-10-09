import { useState } from 'react'
import { App, Descriptions, Divider, Flex, InputNumber, Modal, Select, Table, Tag, Typography } from 'antd'
import { useLocation, useNavigate } from 'react-router'
import { type Schemas } from '../api/client'
import { useActivateConfig, useLogon, useLogout, useRemoveSession, useResetSession, useSetSeqNums, type SessionRef } from '../api/queries'
import { STATUS_LABELS } from './status'
import { StatusBadge } from './StatusBadge'
import { formatCompIds } from './identity'
import { RESET_MODES } from './resetModes'
import { SessionAvailability } from './SessionAvailability'
import { WriteButton } from './WriteButton'

interface Props {
  sessionRef: SessionRef
  detail: Schemas['SessionDetail']
}

export function SessionStatusTab({ sessionRef, detail }: Props) {
  const { summary } = detail
  const logon = useLogon(sessionRef)
  const logout = useLogout(sessionRef)

  return (
    <Flex vertical gap={16}>
      <Descriptions
        size="small"
        column={1}
        bordered
        items={[
          { label: 'Status', children: <StatusBadge status={summary.status} text={STATUS_LABELS[summary.status]} /> },
          { label: 'Group', children: summary.group },
          { label: 'Session', children: summary.id },
          { label: 'Instance', children: `${summary.type.toLowerCase()} ${summary.instanceId}` },
          { label: 'Selected config', children: summary.runningConfigId },
          { label: 'FIX version', children: summary.identity.fixVersion },
          ...(summary.identity.defaultApplVerId ? [{ label: 'DefaultApplVerID', children: summary.identity.defaultApplVerId }] : []),
          { label: 'Sender', children: <Typography.Text copyable>{formatCompIds(summary.identity.sender)}</Typography.Text> },
          { label: 'Target', children: <Typography.Text copyable>{formatCompIds(summary.identity.target)}</Typography.Text> },
          {
            label: 'Desired state',
            children: summary.desiredState === 'LOGGED_OUT' ? <Tag color="orange">logged out by an operator</Tag> : summary.desiredState.toLowerCase().replace('_', ' '),
          },
          { label: 'Next incoming seq num', children: detail.incomingSeqNum },
          { label: 'Next outgoing seq num', children: detail.outgoingSeqNum },
        ]}
      />
      <SessionAvailability key={summary.runningConfigId} configRef={{ ...sessionRef, sessionId: summary.runningConfigId }} />
      <div>
        <Divider titlePlacement="start" plain>
          Session control
        </Divider>
        <Flex wrap gap={8}>
          <WriteButton type="primary" loading={logon.isPending} onConfirm={() => logon.mutate(undefined)}>
            Log on
          </WriteButton>
          <WriteButton danger loading={logout.isPending} onConfirm={() => logout.mutate(undefined)} confirm={`Log out ${summary.group}.${summary.id}?`}>
            Log out
          </WriteButton>
          <SeqNumsButton sessionRef={sessionRef} detail={detail} />
          {summary.type === 'ACCEPTOR' && detail.sessionSettingsStore !== undefined && (
            <RemoveSessionButton sessionRef={sessionRef} detail={detail} />
          )}
        </Flex>
      </div>
      <ResetSection sessionRef={sessionRef} summary={summary} />
      {summary.type === 'INITIATOR' && detail.configs.length > 1 && <Configs sessionRef={sessionRef} detail={detail} />}
    </Flex>
  )
}

function ResetSection({ sessionRef, summary }: { sessionRef: SessionRef; summary: Schemas['SessionSummary'] }) {
  const modes = RESET_MODES.filter((m) => !m.initiatorOnly || summary.type === 'INITIATOR')
  const [mode, setMode] = useState(modes[0]!.mode)
  const reset = useResetSession(sessionRef)
  const selected = RESET_MODES.find((m) => m.mode === mode)!

  return (
    <div>
      <Divider titlePlacement="start" plain>
        Sequence reset
      </Divider>
      <Flex wrap gap={8}>
        <Select value={mode} onChange={setMode} options={modes.map((m) => ({ value: m.mode, label: m.label }))} style={{ minWidth: 240 }} />
        <WriteButton danger loading={reset.isPending} onConfirm={() => reset.mutate({ mode })} confirm={`${selected.label} on ${summary.group}.${summary.id}?`}>
          Reset
        </WriteButton>
      </Flex>
      <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 0 }}>
        {selected.description}
      </Typography.Paragraph>
    </div>
  )
}

/**
 * Acceptor sessions only: an initiator's sessions are the targets it was built with.
 */
function RemoveSessionButton({ sessionRef, detail }: Props) {
  const remove = useRemoveSession(sessionRef)
  const navigate = useNavigate()
  const location = useLocation()
  const { message } = App.useApp()
  return (
    <WriteButton
      danger
      style={{ marginLeft: 'auto' }}
      loading={remove.isPending}
      confirm={`Remove ${sessionRef.group}.${sessionRef.sessionId} from store ${detail.sessionSettingsStore}? The live session is disconnected unless its "Disconnect on remove" setting is off.`}
      onConfirm={() =>
        remove.mutate(undefined, {
          onSuccess: () => navigate({ pathname: '/', search: location.search }),
          onError: (error) => message.error(error.message),
        })
      }
    >
      Remove session
    </WriteButton>
  )
}

function SeqNumsButton({ sessionRef, detail }: Props) {
  const [open, setOpen] = useState(false)
  const [incoming, setIncoming] = useState<number | null>(null)
  const [outgoing, setOutgoing] = useState<number | null>(null)
  const update = useSetSeqNums(sessionRef)

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
        onOk={() => update.mutate({ incoming, outgoing }, { onSuccess: () => setOpen(false) })}
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
  const activate = useActivateConfig(sessionRef)

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
                <Tag color="processing">selected</Tag>
              ) : (
                <WriteButton
                  size="small"
                  loading={activate.isPending}
                  onConfirm={() => activate.mutate(config.id)}
                  confirm={`Switch ${detail.summary.group}.${detail.summary.id} to ${config.id}? The selected config is logged out first.`}
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
