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
import { Alert, Button, Flex, Skeleton, Typography } from 'antd'
import { useState } from 'react'
import { ApiError, type Schemas } from '../api/client'
import {
  useApplicationSettings,
  useCanWrite,
  useSessionComponents,
  useSessionSettings,
  useSessionSettingsSchema,
  useSessionSettingsStores,
  useUpdateSessionSettings,
  type SessionRef,
} from '../api/queries'
import { ApplicationSettingsEditor } from '../settings/ApplicationSettingsEditor'
import { SchemaForm } from '../settings/SchemaForm'
import { problems, type JsonObject, type JsonSchema } from '../settings/schema'
import { sessionComponentOverrides } from './sessionComponentFields'

interface Props {
  sessionRef: SessionRef
  detail: Schemas['SessionDetail']
}

export function SessionSettingsTab({ sessionRef, detail }: Props) {
  const settings = useSessionSettings(sessionRef, detail.summary.runningConfigId)
  const schema = useSessionSettingsSchema(sessionRef.engineId)
  const stores = useSessionSettingsStores(sessionRef.engineId)
  const declared = useApplicationSettings(sessionRef)
  const components = useSessionComponents(sessionRef.engineId)
  const update = useUpdateSessionSettings(sessionRef)
  const canWrite = useCanWrite()
  const [draft, setDraft] = useState<JsonObject>()
  const [invalid, setInvalid] = useState<string[]>([])

  if (settings.isPending || schema.isPending) return <Skeleton active />
  if (settings.isError) return <Alert type="error" title={settings.error.message} />
  if (schema.isError) return <Alert type="error" title={schema.error.message} />

  const stored = settings.data as JsonObject
  const current = draft ?? stored
  const changed = draft !== undefined && JSON.stringify(draft) !== JSON.stringify(stored)
  const store = stores.data?.find((s) => s.instanceId === detail.sessionSettingsStore)
  const editable = canWrite && detail.sessionSettingsStore !== undefined
  const acceptor = detail.summary.type === 'ACCEPTOR'
  const error = update.error

  const save = () => {
    const found = problems(schema.data as JsonSchema, current)
    setInvalid(found)
    if (found.length === 0) {
      update.mutate(current, { onSuccess: () => setDraft(undefined) })
    }
  }

  return (
    <Flex vertical gap={12}>
      {detail.sessionSettingsStore === undefined ? (
        <Alert type="warning" title="No session settings store holds this session: its settings cannot be changed here." />
      ) : store && !store.persistent ? (
        <Alert
          type="info"
          title={`Store ${store.instanceId} keeps changes in memory: the engine forgets them when it restarts.`}
        />
      ) : null}
      {editable && (
        <Flex gap={8} wrap>
          <Button type="primary" disabled={!changed} loading={update.isPending} onClick={save}>
            Save
          </Button>
          <Button disabled={!changed} onClick={() => { setDraft(undefined); setInvalid([]); update.reset() }}>
            Discard
          </Button>
        </Flex>
      )}
      {invalid.length > 0 && <Alert type="error" title="Fix these fields first" description={<Problems list={invalid} />} />}
      {error && (
        <Alert
          type="error"
          title={error.message}
          description={error instanceof ApiError && error.problems.length > 0 ? <Problems list={error.problems} /> : undefined}
        />
      )}
      <Typography.Text type="secondary">Hover a label for the setting's name and what it does.</Typography.Text>
      <SchemaForm
        schema={schema.data as JsonSchema}
        value={current}
        onChange={setDraft}
        disabled={!editable}
        readOnly={acceptor ? ['fixSessionType'] : ['fixSessionType', 'fixSessionId']}
        overrides={{
          ...sessionComponentOverrides(components.data, current),
          fixApplicationSessionSettings: ({ value, onChange }) => (
            <ApplicationSettingsEditor
              value={value}
              onChange={onChange}
              declared={declared.data ?? []}
              stored={stored.fixApplicationSessionSettings as JsonObject | undefined}
            />
          ),
          allowedCertificates: () => (
            <Typography.Text type="secondary">
              Read from the engine's session files and kept as they are when the settings are saved.
            </Typography.Text>
          ),
        }}
      />
    </Flex>
  )
}

function Problems({ list }: { list: string[] }) {
  return (
    <ul style={{ margin: 0, paddingLeft: 20 }}>
      {list.map((problem) => (
        <li key={problem}>{problem}</li>
      ))}
    </ul>
  )
}
