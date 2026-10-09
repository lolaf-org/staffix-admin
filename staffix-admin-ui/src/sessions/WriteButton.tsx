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
