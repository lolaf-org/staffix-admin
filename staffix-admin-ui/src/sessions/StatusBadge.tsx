import { Badge } from 'antd'
import type { ReactNode } from 'react'
import { STATUS_COLORS, STATUS_LABELS, type Status } from './health'

interface Props {
  status: Status
  text?: ReactNode
  title?: string
}

export function StatusBadge({ status, text = STATUS_LABELS[status], title }: Props) {
  const color = STATUS_COLORS[status]
  return color === 'orange'
    ? <Badge color="orange" text={text} title={title} />
    : <Badge status={color} text={text} title={title} />
}
