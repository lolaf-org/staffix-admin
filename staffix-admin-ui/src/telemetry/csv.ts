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
import type { Schemas } from '../api/client'

const HEADER = ['time', 'engine', 'group', 'session', 'type', 'message']
// Excel reads a file without a byte order mark in the system's code page
const BYTE_ORDER_MARK = '﻿'

/**
 * RFC 4180, with the timestamp as the backend gave it so sub-millisecond digits survive.
 */
export function logsCsv(engineId: string, entries: Schemas['LogEntry'][]): string {
  const rows = entries.map((entry) => [entry.timestamp, engineId, entry.group ?? '', entry.sessionId ?? '', entry.type ?? '', entry.line])
  return [HEADER, ...rows].map((row) => row.map(csvField).join(',')).join('\r\n') + '\r\n'
}

export function downloadCsv(fileName: string, csv: string) {
  const url = URL.createObjectURL(new Blob([BYTE_ORDER_MARK, csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function csvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
}
