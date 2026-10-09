import { useState } from 'react'
import { Alert, Divider, Flex, Skeleton, Statistic, theme, Tooltip, Typography } from 'antd'
import { isNotCollected, type Schemas } from '../api/client'
import { useAvailability, type SessionRef } from '../api/queries'
import { formatClockSeconds, formatDateTime, formatDuration } from '../telemetry/format'
import { TimeRangeSelector, type Period } from '../telemetry/TimeRangeSelector'
import { STATUS_LABELS } from './status'

type Shares = Schemas['StatusShares']
type ShareKey = keyof Shares

interface Props {
  /**
   * The session's running config, whose gauge the engine exports.
   */
  configRef: SessionRef
}

const DEFAULT_MINUTES = 1440
const BAND_HEIGHT = 28

export function SessionAvailability({ configRef }: Props) {
  const [period, setPeriod] = useState<Period>(DEFAULT_MINUTES)
  const query = useAvailability(configRef, period)
  const states = useStates()

  return (
    <div>
      <Divider titlePlacement="start" plain>
        Availability
      </Divider>
      <Flex vertical gap={12}>
        <TimeRangeSelector value={period} onChange={setPeriod} />
        {query.isError && <Alert type={isNotCollected(query.error) ? 'info' : 'error'} title={query.error.message} />}
        {query.isPending && <Skeleton.Button active block style={{ height: BAND_HEIGHT }} />}
        {query.isSuccess && (
          <>
            <Band steps={query.data.availability.steps} states={states} />
            <Summary
              availability={query.data.availability}
              periodMillis={Date.parse(query.data.range.to) - Date.parse(query.data.range.from)}
              states={states}
            />
          </>
        )}
      </Flex>
    </div>
  )
}

interface State {
  key: ShareKey
  label: string
  color: string
}

/**
 * Stacked bottom to top in this order, as the status colours.
 */
function useStates(): State[] {
  const { token } = theme.useToken()
  return [
    { key: 'loggedIn', label: STATUS_LABELS.LOGGED_IN, color: token.colorSuccess },
    { key: 'loggedOutInsideSessionTime', label: STATUS_LABELS.LOGGED_OUT_INSIDE_SESSION_TIME, color: token.colorError },
    { key: 'loggedOutByOperator', label: STATUS_LABELS.LOGGED_OUT_BY_OPERATOR, color: token.orange },
    { key: 'loggedOutOutsideSessionTime', label: STATUS_LABELS.LOGGED_OUT_OUTSIDE_SESSION_TIME, color: token.colorWarning },
    { key: 'noData', label: 'No data', color: token.colorFillSecondary },
  ]
}

function Band({ steps, states }: { steps: Schemas['AvailabilityStep'][]; states: State[] }) {
  const stepMillis = steps.length > 1 ? Date.parse(steps[1]!.end) - Date.parse(steps[0]!.end) : 0

  return (
    <div>
      <Flex style={{ height: BAND_HEIGHT }} role="img" aria-label="Status over the period">
        {steps.map((step) => (
          <Tooltip
            key={step.end}
            title={
              <>
                <div>
                  {formatDateTime(Date.parse(step.end) - stepMillis)} to {formatClockSeconds(Date.parse(step.end))}
                </div>
                {states
                  .filter((state) => step.shares[state.key] > 0)
                  .map((state) => (
                    <div key={state.key}>
                      {state.label}: {formatPercent(step.shares[state.key])}
                    </div>
                  ))}
              </>
            }
          >
            <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column-reverse' }}>
              {states.map((state) => (
                <div key={state.key} style={{ flexGrow: step.shares[state.key], flexBasis: 0, background: state.color }} />
              ))}
            </div>
          </Tooltip>
        ))}
      </Flex>
      {steps.length > 0 && (
        <Flex justify="space-between">
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {formatDateTime(Date.parse(steps[0]!.end) - stepMillis)}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {formatDateTime(Date.parse(steps[steps.length - 1]!.end))}
          </Typography.Text>
        </Flex>
      )}
    </div>
  )
}

function Summary({ availability, periodMillis, states }: { availability: Schemas['Availability']; periodMillis: number; states: State[] }) {
  return (
    <Flex wrap gap={24} align="center">
      <Tooltip title="Logged in over the time it was meant to be: an operator logout and missing data do not count against it">
        <Statistic
          title="Within session time"
          value={availability.ratio == null ? 'never meant to be up' : formatPercent(availability.ratio)}
        />
      </Tooltip>
      <Flex vertical gap={2}>
        {states
          .filter((state) => availability.total[state.key] > 0)
          .map((state) => (
            <Flex key={state.key} gap={8} align="center">
              <span style={{ width: 10, height: 10, borderRadius: 2, background: state.color }} />
              <Typography.Text>
                {state.label}: {formatDuration(availability.total[state.key] * periodMillis)}
              </Typography.Text>
            </Flex>
          ))}
      </Flex>
    </Flex>
  )
}

function formatPercent(share: number): string {
  return share === 1 ? '100 %' : `${(Math.floor(share * 1000) / 10).toFixed(1)} %`
}
