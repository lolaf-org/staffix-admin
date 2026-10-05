import { Alert, Form, Modal, Select, Skeleton, Tag } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError, type Schemas } from '../api/client'
import {
  fetchSessionSettings,
  useAddSession,
  useApplicationSettings,
  useSessionComponents,
  useSessionSettingsSchema,
  useSessionSettingsStores,
  type SessionRef,
} from '../api/queries'
import { ApplicationSettingsEditor } from '../settings/ApplicationSettingsEditor'
import { SchemaForm } from '../settings/SchemaForm'
import { problems, SECRET_MASK, type JsonObject, type JsonSchema } from '../settings/schema'
import { sessionComponentOverrides } from './sessionComponentFields'

const NEW_ACCEPTOR: JsonObject = { fixSessionType: 'ACCEPTOR', fixSessionId: {} }

interface Props {
  engineId: string
  open: boolean
  onClose: () => void
  /**
   * The engine's sessions, the acceptor ones offered as a starting point.
   */
  groups: Schemas['SessionGroup'][]
}

/**
 * Adds an acceptor session: an initiator's sessions are the targets it was built with, so the console adds none.
 */
export function AddSessionModal({ engineId, open, onClose, groups }: Props) {
  const schema = useSessionSettingsSchema(engineId)
  const stores = useSessionSettingsStores(engineId)
  const add = useAddSession(engineId)
  const [store, setStore] = useState<string>()
  const [source, setSource] = useState<SessionRef>()
  const declared = useApplicationSettings(source)
  const components = useSessionComponents(engineId)
  const [draft, setDraft] = useState<JsonObject>(NEW_ACCEPTOR)
  const [secretsDropped, setSecretsDropped] = useState(false)
  const [invalid, setInvalid] = useState<string[]>([])

  useEffect(() => {
    if (open) {
      setDraft(NEW_ACCEPTOR)
      setSource(undefined)
      setSecretsDropped(false)
      setInvalid([])
      add.reset()
      setStore(stores.data?.find((s) => s.persistent)?.instanceId ?? stores.data?.[0]?.instanceId)
    }
  }, [open, stores.data])

  const startFrom = async (ref: SessionRef | undefined) => {
    setSource(ref)
    if (!ref) {
      setDraft(NEW_ACCEPTOR)
      return
    }
    const settings = (await fetchSessionSettings(ref)) as JsonObject
    const application = { ...((settings.fixApplicationSessionSettings as JsonObject | undefined) ?? {}) }
    const secrets = Object.keys(application).filter((id) => application[id] === SECRET_MASK)
    secrets.forEach((id) => delete application[id])
    setSecretsDropped(secrets.length > 0)
    setDraft({ ...settings, fixApplicationSessionSettings: application })
  }

  const save = () => {
    const found = problems(schema.data as JsonSchema, draft)
    setInvalid(found)
    if (found.length === 0 && store) {
      add.mutate({ store, settings: draft }, { onSuccess: onClose })
    }
  }

  const acceptors = groups.flatMap((group) =>
    group.sessions.filter((session) => session.type === 'ACCEPTOR').map((session) => ({ group: group.group, sessionId: session.id })),
  )

  return (
    <Modal
      title={`New acceptor session on ${engineId}`}
      open={open}
      onCancel={onClose}
      onOk={save}
      okText="Add"
      okButtonProps={{ loading: add.isPending, disabled: !store }}
      width={900}
      destroyOnHidden
    >
      {schema.isPending || stores.isPending ? (
        <Skeleton active />
      ) : schema.isError ? (
        <Alert type="error" title={schema.error.message} />
      ) : (
        <>
          <Form layout="vertical" component="div">
            <Form.Item label="Session settings store" required>
              <Select
                value={store}
                onChange={setStore}
                options={stores.data?.map((s) => ({
                  value: s.instanceId,
                  label: <>{s.instanceId} {!s.persistent && <Tag>kept in memory, lost on restart</Tag>}</>,
                }))}
              />
            </Form.Item>
            <Form.Item label="Start from" extra="Copies an acceptor session's settings; change at least its name.">
              <Select
                allowClear
                placeholder="A blank session"
                value={source ? `${source.group}.${source.sessionId}` : undefined}
                onChange={(key?: string) => startFrom(acceptors.map((a) => ({ engineId, ...a })).find((a) => `${a.group}.${a.sessionId}` === key))}
                options={acceptors.map((a) => ({ value: `${a.group}.${a.sessionId}`, label: `${a.group}.${a.sessionId}` }))}
              />
            </Form.Item>
          </Form>
          {secretsDropped && <Alert type="info" title="Secret application settings are not copied: type them again." style={{ marginBottom: 12 }} />}
          {invalid.length > 0 && <Alert type="error" title="Fix these fields first" description={invalid.join(', ')} style={{ marginBottom: 12 }} />}
          {add.error && (
            <Alert
              type="error"
              title={add.error.message}
              description={add.error instanceof ApiError && add.error.problems.length > 0 ? add.error.problems.join(', ') : undefined}
              style={{ marginBottom: 12 }}
            />
          )}
          <SchemaForm
            schema={schema.data as JsonSchema}
            value={draft}
            onChange={setDraft}
            readOnly={['fixSessionType']}
            overrides={{
              ...sessionComponentOverrides(components.data, draft),
              fixApplicationSessionSettings: ({ value, onChange }) => (
                <ApplicationSettingsEditor value={value} onChange={onChange} declared={source ? (declared.data ?? []) : []} />
              ),
              allowedCertificates: () => <>Configured in the engine's session files.</>,
            }}
          />
        </>
      )}
    </Modal>
  )
}
