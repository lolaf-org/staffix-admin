import { Button, Popconfirm, Tooltip, type ButtonProps } from 'antd'
import type { ReactNode } from 'react'
import { useCanWrite } from '../api/queries'

interface Props extends Omit<ButtonProps, 'onClick'> {
  onConfirm: () => void
  /**
   * When set, the operator confirms before the action runs.
   */
  confirm?: ReactNode
}

/**
 * A state-changing action: disabled, with the reason, for readers.
 */
export function WriteButton({ onConfirm, confirm, disabled, ...button }: Props) {
  const canWrite = useCanWrite()
  if (!canWrite) {
    return (
      <Tooltip title="Requires the writer role">
        <Button {...button} disabled />
      </Tooltip>
    )
  }
  if (!confirm) {
    return <Button {...button} disabled={disabled} onClick={onConfirm} />
  }
  return (
    <Popconfirm title={confirm} onConfirm={onConfirm} okText="Confirm" disabled={disabled}>
      <Button {...button} disabled={disabled} />
    </Popconfirm>
  )
}
