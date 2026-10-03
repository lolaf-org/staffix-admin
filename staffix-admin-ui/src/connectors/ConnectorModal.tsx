import { Alert, Button, Form, Input, Modal, Select, Switch, Tag } from 'antd'
import { useEffect, useMemo } from 'react'
import { type Connector, type ConnectorKind, useCanWrite, useTestConnector } from '../api/queries'
import { ROLE_LABELS, SET } from './draft'

type Setting = ConnectorKind['settings'][number]
type SettingValue = string | boolean | string[] | undefined

interface FormValues {
  id: string
  name: string
  kind: string
  settings: Record<string, SettingValue>
}

interface Props {
  open: boolean
  /**
   * Undefined to create one.
   */
  connector?: Connector
  kinds: ConnectorKind[]
  takenIds: string[]
  onSave: (connector: Connector) => void
  onClose: () => void
}

export function ConnectorModal({ open, connector, kinds, takenIds, onSave, onClose }: Props) {
  const [form] = Form.useForm<FormValues>()
  const canWrite = useCanWrite()
  const test = useTestConnector()
  const kindName = Form.useWatch('kind', form)
  const kind = useMemo(() => kinds.find((k) => k.kind === kindName), [kinds, kindName])

  useEffect(() => {
    if (open) {
      test.reset()
      form.resetFields()
      form.setFieldsValue(connector ? toForm(connector) : { settings: {} })
    }
  }, [open, connector])

  const build = async (): Promise<Connector> => toConnector(await form.validateFields(), kind, connector)

  return (
    <Modal
      title={connector ? `Connector ${connector.name}` : 'New connector'}
      open={open}
      onCancel={onClose}
      destroyOnHidden
      footer={[
        <Button key="test" disabled={!canWrite || !kind} loading={test.isPending} onClick={async () => test.mutate(await build())}>
          Test connection
        </Button>,
        <Button key="cancel" onClick={onClose}>
          {canWrite ? 'Cancel' : 'Close'}
        </Button>,
        canWrite && (
          <Button key="ok" type="primary" onClick={async () => onSave(await build())}>
            Apply
          </Button>
        ),
      ]}
    >
      <Form<FormValues> form={form} layout="vertical" disabled={!canWrite} requiredMark="optional">
        <Form.Item
          name="id"
          label="Id"
          extra="How engines and defaults refer to it; it cannot change once used."
          rules={[
            { required: true, pattern: /^[a-z0-9][a-z0-9-]*$/, message: 'Lower case letters, digits and dashes' },
            { validator: async (_, id) => { if (!connector && takenIds.includes(id)) throw new Error('Already taken') } },
          ]}
        >
          <Input disabled={connector !== undefined} />
        </Form.Item>
        <Form.Item name="name" label="Name" rules={[{ required: true, whitespace: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="kind" label="Kind" rules={[{ required: true }]}>
          <Select
            disabled={connector !== undefined}
            options={kinds.map((k) => ({ value: k.kind, label: <>{k.kind} <Tag>{ROLE_LABELS[k.role]}</Tag></> }))}
          />
        </Form.Item>
        {kind?.settings.map((setting) => (
          <SettingItem key={setting.name} setting={setting} stored={connector?.settings[setting.name]} />
        ))}
      </Form>
      {test.isSuccess && <Alert type="success" title="The backend answered" />}
      {test.isError && <Alert type="error" title="The connection test failed" description={test.error.message} />}
    </Modal>
  )
}

function SettingItem({ setting, stored }: { setting: Setting; stored: unknown }) {
  const name = ['settings', setting.name]
  const hint = setting.defaultValue ? `Default: ${setting.defaultValue}` : undefined
  const rules = setting.required && !(setting.secret && stored === SET) ? [{ required: true }] : []
  if (setting.secret) {
    return (
      <Form.Item
        name={name}
        label={setting.name} tooltip={setting.description ?? undefined}
        rules={rules}
        extra="Secret: a reference such as ${env:NAME} or ${file:/run/secrets/name}, or a value the console stores encrypted."
      >
        <Input.Password placeholder={stored === SET ? 'Stored; type to replace it' : undefined} autoComplete="new-password" />
      </Form.Item>
    )
  }
  switch (setting.type) {
    case 'BOOLEAN':
      return (
        <Form.Item name={name} label={setting.name} tooltip={setting.description ?? undefined} valuePropName="checked" extra={hint}>
          <Switch />
        </Form.Item>
      )
    case 'ENUM':
      return (
        <Form.Item name={name} label={setting.name} tooltip={setting.description ?? undefined} rules={rules} extra={hint}>
          <Select allowClear options={setting.values.map((v) => ({ value: v, label: v }))} />
        </Form.Item>
      )
    case 'LIST':
      return (
        <Form.Item name={name} label={setting.name} tooltip={setting.description ?? undefined} rules={rules} extra={hint}>
          {setting.values.length > 0 ? (
            <Select mode="multiple" allowClear options={setting.values.map((v) => ({ value: v, label: v }))} />
          ) : (
            <Select mode="tags" open={false} placeholder="Type a value and press enter" />
          )}
        </Form.Item>
      )
    default:
      return (
        <Form.Item name={name} label={setting.name} tooltip={setting.description ?? undefined} rules={rules} extra={hint}>
          <Input placeholder={setting.defaultValue ?? undefined} />
        </Form.Item>
      )
  }
}

function toForm(connector: Connector): Partial<FormValues> {
  const settings: Record<string, SettingValue> = {}
  Object.entries(connector.settings).forEach(([name, value]) => {
    if (value === SET) return
    settings[name] = Array.isArray(value) ? value.map(String) : typeof value === 'boolean' ? value : String(value)
  })
  return { id: connector.id, name: connector.name, kind: connector.kind, settings }
}

/**
 * Empty settings are left out so their default applies; an untouched stored secret is sent back as {@link SET}.
 */
function toConnector(values: FormValues, kind: ConnectorKind | undefined, stored: Connector | undefined): Connector {
  const settings: Record<string, unknown> = {}
  kind?.settings.forEach((setting) => {
    const value = values.settings?.[setting.name]
    const empty = value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
    if (!empty) {
      settings[setting.name] = value
    } else if (setting.secret && stored?.settings[setting.name] === SET) {
      settings[setting.name] = SET
    }
  })
  return { id: values.id, name: values.name.trim(), kind: values.kind, settings }
}
