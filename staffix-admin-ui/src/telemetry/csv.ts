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
