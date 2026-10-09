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
import { Alert } from 'antd'

/**
 * Sessions of the scope whose telemetry no connector collects, so a chart or a log list missing them is not
 * mistaken for an idle session.
 */
export function NotCollectedNotice({ what, sessions }: { what: 'metrics' | 'logs'; sessions: string[] }) {
  if (sessions.length === 0) return null
  return (
    <Alert
      type="info"
      showIcon
      title={`The ${what} of ${sessions.length === 1 ? 'one session are' : `${sessions.length} sessions are`} not collected`}
      description={`${sessions.join(', ')}: no connector is assigned to their ${what === 'metrics' ? 'monitoring plugin' : 'messages logger'}, or it exports nothing a backend reads.`}
    />
  )
}
