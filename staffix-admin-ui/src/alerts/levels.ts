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
import type { GlobalToken } from 'antd'
import type { Schemas } from '../api/client'

export type AlertLevel = Schemas['ActiveAlert']['level']

/**
 * The worst first, as every list of them is shown.
 */
export const ALERT_LEVELS: AlertLevel[] = ['CRITICAL', 'MAJOR', 'WARNING']

export const ALERT_LEVEL_LABELS: Record<AlertLevel, string> = {
  CRITICAL: 'Critical',
  MAJOR: 'Major',
  WARNING: 'Warning',
}

/**
 * The status palette, drawn on a bell rather than the box fill, so a level never reads as a session status.
 */
export function alertLevelColor(level: AlertLevel, token: GlobalToken): string {
  return { CRITICAL: token.colorError, MAJOR: token.orange, WARNING: token.colorWarning }[level]
}

export function worstLevel(levels: (AlertLevel | null | undefined)[]): AlertLevel | undefined {
  return ALERT_LEVELS.find((level) => levels.includes(level))
}
