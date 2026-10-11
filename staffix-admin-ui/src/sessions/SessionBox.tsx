/*
 * Copyright © 2026 Lolaf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Flex, Tooltip, Typography, theme } from 'antd'
import { PauseCircleOutlined } from '@ant-design/icons'
import type { Schemas } from '../api/client'
import { STATUS_COLORS, STATUS_LABELS } from './status'
import { formatIdentity } from './identity'
import { AlertBell } from '../alerts/AlertBell'
import { ALERT_LEVEL_LABELS } from '../alerts/levels'

interface Props {
  session: Schemas['SessionSummary']
  /**
   * The session's ongoing alerts for the tooltip; may lag the summary's count by a poll.
   */
  alerts: Schemas['ActiveAlert'][]
  selected: boolean
  onClick: () => void
  /**
   * The engine does not answer: dimmed, without its alert bell, its last known state only in the tooltip.
   */
  stale?: boolean
}

/**
 * 50% over Ant Design's 250 px, so a full FIX session id usually fits on one line.
 */
const TOOLTIP_MAX_WIDTH = 375

export function SessionBox({ session, alerts, selected, onClick, stale = false }: Props) {
  const { token } = theme.useToken()
  const colors = {
    success: { background: token.colorSuccessBg, border: token.colorSuccess },
    warning: { background: token.colorWarningBg, border: token.colorWarning },
    orange: { background: token.orange1, border: token.orange },
    error: { background: token.colorErrorBg, border: token.colorError },
  }[STATUS_COLORS[session.status]]
  const operatorLoggedOut = session.desiredState === 'LOGGED_OUT'
  const mono = { fontFamily: token.fontFamilyCode, fontSize: 12 }

  return (
    <Tooltip
      styles={{ root: { maxWidth: TOOLTIP_MAX_WIDTH } }}
      title={
        <>
          <div>{formatIdentity(session.identity)}</div>
          {stale && <div>Last known state: the engine does not answer</div>}
          <div>{STATUS_LABELS[session.status]}</div>
          {stale && session.alertLevel && <div>Last known worst alert: {ALERT_LEVEL_LABELS[session.alertLevel]}</div>}
          {!stale && alerts.map((alert) => (
            <div key={alert.probe}>
              {ALERT_LEVEL_LABELS[alert.level]}: {alert.probe}, {alert.message}
            </div>
          ))}
        </>
      }
    >
      <button
        type="button"
        onClick={onClick}
        aria-label={stale
          ? `${session.group}.${session.id}, last known ${STATUS_LABELS[session.status]}, alerts unknown`
          : `${session.group}.${session.id}, ${STATUS_LABELS[session.status]}${session.alertCount > 0 ? `, ${session.alertCount} alert${session.alertCount > 1 ? 's' : ''}` : ''}`}
        style={{
          width: 220,
          padding: '8px 10px',
          textAlign: 'left',
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          background: colors.background,
          border: `1px ${stale ? 'dashed' : 'solid'} ${colors.border}`,
          borderLeft: `6px ${stale ? 'dashed' : 'solid'} ${colors.border}`,
          opacity: stale ? 0.55 : undefined,
          filter: stale ? 'grayscale(0.7)' : undefined,
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
          <Flex align="center" gap={6} style={{ flexShrink: 0 }}>
            {!stale && session.alertLevel && <AlertBell level={session.alertLevel} count={session.alertCount} tooltip={false} />}
            <Typography.Text type="secondary" style={{ fontSize: 11 }}>
              {session.type.toLowerCase()}
            </Typography.Text>
          </Flex>
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
