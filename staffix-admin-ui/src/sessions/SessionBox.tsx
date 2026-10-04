import { Flex, Tooltip, Typography, theme } from 'antd'
import { PauseCircleOutlined } from '@ant-design/icons'
import type { Schemas } from '../api/client'
import { HEALTH_LABELS, STATUS_COLORS, STATUS_LABELS, statusOf } from './health'
import { formatIdentity } from './identity'

interface Props {
  session: Schemas['SessionSummary']
  selected: boolean
  onClick: () => void
}

/**
 * 50% over Ant Design's 250 px, so a full FIX session id usually fits on one line.
 */
const TOOLTIP_MAX_WIDTH = 375

export function SessionBox({ session, selected, onClick }: Props) {
  const { token } = theme.useToken()
  const colors = {
    success: { background: token.colorSuccessBg, border: token.colorSuccess },
    warning: { background: token.colorWarningBg, border: token.colorWarning },
    orange: { background: token.orange1, border: token.orange },
    error: { background: token.colorErrorBg, border: token.colorError },
  }[STATUS_COLORS[statusOf(session)]]
  const operatorLoggedOut = session.desiredState === 'LOGGED_OUT'
  const mono = { fontFamily: token.fontFamilyCode, fontSize: 12 }

  return (
    <Tooltip
      styles={{ root: { maxWidth: TOOLTIP_MAX_WIDTH } }}
      title={
        <>
          <div>{formatIdentity(session.identity)}</div>
          <div>{HEALTH_LABELS[session.health]}</div>
          {operatorLoggedOut && <div>{STATUS_LABELS.LOGGED_OUT_BY_OPERATOR}</div>}
        </>
      }
    >
      <button
        type="button"
        onClick={onClick}
        aria-label={`${session.group}.${session.id}, ${STATUS_LABELS[statusOf(session)]}`}
        style={{
          width: 220,
          padding: '8px 10px',
          textAlign: 'left',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          background: colors.background,
          border: `1px solid ${colors.border}`,
          borderLeft: `6px solid ${colors.border}`,
          borderRadius: token.borderRadius,
          outline: selected ? `2px solid ${token.colorPrimary}` : undefined,
          outlineOffset: 2,
        }}
      >
        <Flex justify="space-between" align="baseline" gap={8} style={{ width: '100%' }}>
          <Typography.Text strong ellipsis style={{ fontFamily: token.fontFamilyCode }}>
            {session.id}
            {operatorLoggedOut && <PauseCircleOutlined style={{ marginLeft: 6 }} aria-label="Logged out by an operator" />}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {session.type.toLowerCase()}
          </Typography.Text>
        </Flex>
        {session.configIds.length > 1 &&
          session.configIds.map((configId, index) => {
            const running = configId === session.runningConfigId
            return (
              <Typography.Text key={configId} type={running ? undefined : 'secondary'} strong={running} style={mono}>
                {running ? '●' : '○'} {configId} {index === 0 ? 'main' : 'backup'}
              </Typography.Text>
            )
          })}
        <Typography.Text type="secondary" ellipsis style={{ fontSize: 11 }}>
          instance {session.instanceId}
        </Typography.Text>
      </button>
    </Tooltip>
  )
}
