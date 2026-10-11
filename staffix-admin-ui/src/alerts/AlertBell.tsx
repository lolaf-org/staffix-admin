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
import { Tooltip, theme } from 'antd'
import { BellFilled, QuestionOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { ALERT_LEVEL_LABELS, alertLevelColor, type AlertLevel } from './levels'

interface Props {
  level: AlertLevel
  count: number
  /**
   * False inside an element that has its own tooltip, which then lists the alerts.
   */
  tooltip?: ReactNode | false
  onClick?: () => void
}

/**
 * A filled pill rather than a bare icon, so it stands out on a coloured session box; the count shows from two.
 */
export function AlertBell({ level, count, tooltip, onClick }: Props) {
  const { token } = theme.useToken()
  const label = `${count} alert${count > 1 ? 's' : ''}, worst ${ALERT_LEVEL_LABELS[level].toLowerCase()}`
  const pill = (
    <span
      role={onClick ? 'button' : 'img'}
      aria-label={label}
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '1px 7px',
        borderRadius: 10,
        background: alertLevelColor(level, token),
        color: '#fff',
        fontSize: 13,
        fontWeight: 600,
        lineHeight: '18px',
        cursor: onClick ? 'pointer' : undefined,
        whiteSpace: 'nowrap',
      }}
    >
      <BellFilled style={{ fontSize: 14 }} />
      {count > 1 && count}
    </span>
  )
  return tooltip === false ? pill : <Tooltip title={tooltip ?? label}>{pill}</Tooltip>
}

/**
 * In place of the bells while an engine does not answer, so a quiet card is never mistaken for one without alerts.
 */
export function UnknownAlertsPill() {
  const { token } = theme.useToken()
  return (
    <Tooltip title="Alerts unknown: the engine does not answer; its sessions show their last known state">
      <span
        role="img"
        aria-label="Alerts unknown"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          padding: '1px 7px',
          borderRadius: 10,
          background: token.colorTextQuaternary,
          color: '#fff',
          fontSize: 13,
          fontWeight: 600,
          lineHeight: '18px',
          whiteSpace: 'nowrap',
        }}
      >
        <BellFilled style={{ fontSize: 14 }} />
        <QuestionOutlined style={{ fontSize: 12 }} />
        alerts unknown
      </span>
    </Tooltip>
  )
}
