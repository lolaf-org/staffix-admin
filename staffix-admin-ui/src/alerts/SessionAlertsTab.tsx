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
import { Alert, Skeleton, Table, Tooltip, Typography } from 'antd'
import type { Schemas } from '../api/client'
import { useSessionProbes, type SessionRef } from '../api/queries'
import { parseDuration } from '../settings/duration'
import { formatDateTime } from '../telemetry/format'
import { AlertLevelTag } from './AlertLevelTag'
import { AlertSince } from './AlertSince'
import { ClearAlertButton } from './ClearAlertButton'
import { RunbookLink } from './RunbookLink'
import { ALERT_LEVELS } from './levels'

type SessionProbe = Schemas['SessionProbe']

/**
 * The probes in alert first, the worst first; then the others in the order the engine added them.
 */
function inAlertFirst(probes: SessionProbe[]): SessionProbe[] {
  const rank = (probe: SessionProbe) => (probe.level ? ALERT_LEVELS.indexOf(probe.level) : ALERT_LEVELS.length)
  return [...probes].sort((a, b) => rank(a) - rank(b))
}

function normalState(probe: SessionProbe) {
  if (probe.suspended) {
    return (
      <Tooltip title="The session is not logged in, and this probe runs only while it is">
        <Typography.Text type="secondary">Suspended</Typography.Text>
      </Tooltip>
    )
  }
  return <Typography.Text type="secondary">{probe.lastEvaluation ? 'Normal' : 'Not evaluated yet'}</Typography.Text>
}

function lastEvaluated(probe: SessionProbe): string {
  if (!probe.lastEvaluation) return 'Not evaluated yet'
  return `Last evaluated ${formatDateTime(Date.parse(probe.lastEvaluation))}${probe.suspended ? ', before the logout' : ''}`
}

function interval(iso: string | null | undefined): string {
  const duration = iso ? parseDuration(iso) : undefined
  return duration ? `${duration.amount} ${duration.unit}` : ''
}

export function SessionAlertsTab({ sessionRef }: { sessionRef: SessionRef }) {
  const probes = useSessionProbes(sessionRef)

  if (probes.isPending) return <Skeleton active />
  if (probes.isError) return <Alert type="error" title={probes.error.message} />

  const now = Date.now()
  return (
    <Table<SessionProbe>
      size="small"
      rowKey="name"
      dataSource={inAlertFirst(probes.data)}
      pagination={false}
      columns={[
        {
          title: 'State',
          width: 100,
          render: (_, probe) => (probe.level ? <AlertLevelTag level={probe.level} /> : normalState(probe)),
        },
        {
          title: 'Probe',
          render: (_, probe) => (
            <div style={{ whiteSpace: 'nowrap' }}>
              <Tooltip title={probe.description}>
                <Typography.Text code>{probe.name}</Typography.Text>
              </Tooltip>
              <div><RunbookLink url={probe.runbookUrl} /></div>
            </div>
          ),
        },
        { title: 'Message', dataIndex: 'message' },
        {
          title: 'Since',
          render: (_, probe) => probe.since && <AlertSince since={probe.since} now={now} />,
        },
        {
          title: 'Evaluated',
          render: (_, probe) => (
            <Tooltip title={lastEvaluated(probe)}>
              <Typography.Text type="secondary" style={{ whiteSpace: 'nowrap' }}>
                every {interval(probe.evaluationInterval)}
                {probe.onlyWhileLoggedIn && <> while logged in</>}
                {probe.reminderInterval && <>, reminded every {interval(probe.reminderInterval)}</>}
              </Typography.Text>
            </Tooltip>
          ),
        },
        {
          width: 80,
          render: (_, probe) => probe.level && <ClearAlertButton sessionRef={sessionRef} probe={probe.name} />,
        },
      ]}
    />
  )
}
