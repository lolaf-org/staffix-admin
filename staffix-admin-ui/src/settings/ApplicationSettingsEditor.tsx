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
import { Divider, Form, Input, Typography } from 'antd'
import type { ApplicationSetting } from '../api/queries'
import { MapEditor } from './SchemaForm'
import { SECRET_MASK, type JsonObject, type JsonValue } from './schema'

interface Props {
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
  /**
   * What the session's application declares; none for a session not yet started.
   */
  declared: ApplicationSetting[]
  /**
   * The values as the engine sent them, where a stored secret reads as the mask.
   */
  stored?: JsonObject
}

/**
 * The settings the session's application declares, each described, a secret one typed blind and kept when left
 * empty; then any other setting the session carries.
 */
export function ApplicationSettingsEditor({ value, onChange, declared, stored }: Props) {
  const values: JsonObject = value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const declaredIds = declared.map((setting) => setting.id)
  const update = (next: JsonObject) => onChange(Object.keys(next).length > 0 ? next : undefined)
  const set = (id: string, text: string, secret: boolean) => {
    const next = { ...values }
    const kept = secret && stored?.[id] === SECRET_MASK ? SECRET_MASK : undefined
    if (text === '' && kept === undefined) delete next[id]
    else next[id] = text === '' ? kept! : text
    update(next)
  }
  const others = Object.fromEntries(Object.entries(values).filter(([id]) => !declaredIds.includes(id)))
  return (
    <>
      {declared.map((setting) => {
        const current = values[setting.id]
        return (
          <Form.Item key={setting.id} label={<code>{setting.id}</code>} tooltip={setting.description ?? undefined}>
            {setting.secret ? (
              <Input.Password
                autoComplete="new-password"
                value={current === SECRET_MASK ? '' : ((current as string | undefined) ?? '')}
                placeholder={current === SECRET_MASK ? 'Stored; type to replace it' : 'Not set'}
                onChange={(event) => set(setting.id, event.target.value, true)}
              />
            ) : (
              <Input allowClear value={(current as string | undefined) ?? ''} placeholder="Not set" onChange={(event) => set(setting.id, event.target.value, false)} />
            )}
          </Form.Item>
        )
      })}
      {declared.length > 0 && <Divider plain titlePlacement="start">Other settings</Divider>}
      {declared.length === 0 && (
        <Typography.Paragraph type="secondary">Settings the application reads, by name.</Typography.Paragraph>
      )}
      <MapEditor
        value={Object.keys(others).length > 0 ? others : undefined}
        onChange={(next) => update({ ...Object.fromEntries(Object.entries(values).filter(([id]) => declaredIds.includes(id))), ...(next ?? {}) })}
      />
    </>
  )
}
