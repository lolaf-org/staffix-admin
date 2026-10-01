import { Tooltip, Typography, theme } from 'antd'
import { PauseCircleOutlined } from '@ant-design/icons'
import type { Schemas } from '../api/client'
import { HEALTH_LABELS, HEALTH_STATUS } from './health'

interface Props {
  session: Schemas['SessionSummary']
  selected: boolean
  onClick: () => void
}

export function SessionBox({ session, selected, onClick }: Props) {
  const { token } = theme.useToken()
  const status = HEALTH_STATUS[session.health]
  const colors = {
    success: { background: token.colorSuccessBg, border: token.colorSuccess },
    warning: { background: token.colorWarningBg, border: token.colorWarning },
    error: { background: token.colorErrorBg, border: token.colorError },
  }[status]
  const operatorLoggedOut = session.desiredState === 'LOGGED_OUT'

  return (
    <Tooltip
      title={
        <>
          <div>{session.fixSessionId}</div>
          <div>{HEALTH_LABELS[session.health]}</div>
          {operatorLoggedOut && <div>Logged out by an operator</div>}
        </>
      }
    >
      <button
        type="button"
        onClick={onClick}
        style={{
          width: 148,
          padding: '8px 10px',
          textAlign: 'left',
          cursor: 'pointer',
          background: colors.background,
          border: `1px solid ${colors.border}`,
          borderLeft: `6px solid ${colors.border}`,
          borderRadius: token.borderRadius,
          outline: selected ? `2px solid ${token.colorPrimary}` : undefined,
          outlineOffset: 2,
        }}
      >
        <Typography.Text strong ellipsis style={{ display: 'block' }}>
          {session.id}
        </Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {session.type === 'INITIATOR' ? 'initiator' : 'acceptor'}
          {operatorLoggedOut && <PauseCircleOutlined style={{ marginLeft: 6 }} aria-label="Logged out by an operator" />}
        </Typography.Text>
      </button>
    </Tooltip>
  )
}
