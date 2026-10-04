import { Button, Card, Col, Collapse, Flex, Form, Input, InputNumber, Row, Select, Space, Switch, Tag, Tooltip } from 'antd'
import { DeleteOutlined, PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { DURATION_UNITS, type DurationUnit, formatDuration, parseDuration } from './duration'
import { getAt, humanize, kindOf, resolve, setAt, type JsonObject, type JsonSchema, type JsonValue } from './schema'

/**
 * Replaces the editor of one top-level field, for a field the schema cannot say enough about (secrets, read-only
 * values).
 */
export type FieldOverride = (props: { value: JsonValue | undefined; onChange: (value: JsonValue | undefined) => void }) => ReactNode

interface Props {
  schema: JsonSchema
  value: JsonObject
  onChange: (value: JsonObject) => void
  disabled?: boolean
  overrides?: Record<string, FieldOverride>
  /**
   * Top-level fields and sections shown but not editable, such as the identity of a session the engine fixes.
   */
  readOnly?: string[]
}

/**
 * A form for any document of the schema: every field with its description as a tooltip and its default shown when
 * left unset, which is what the engine then applies.
 */
export function SchemaForm({ schema, value, onChange, disabled, overrides = {}, readOnly = [] }: Props) {
  const properties = Object.entries(schema.properties ?? {})
  const sections = properties.filter(([, node]) => kindOf(resolve(node, schema)) === 'object' || kindOf(resolve(node, schema)) === 'objectList')
  const general = properties.filter(([name]) => !sections.some(([section]) => section === name))
  const set = (path: string[], next: JsonValue | undefined) => onChange(setAt(value, path, next))
  const fields = (entries: [string, JsonSchema][], path: string[], parent: JsonSchema) => (
    <Row gutter={16}>
      {entries.map(([name, node]) => {
        const fieldPath = [...path, name]
        const resolved = resolve(node, schema)
        const fieldValue = getAt(value, fieldPath)
        const override = path.length === 0 ? overrides[name] : undefined
        return (
          <Col key={name} xs={24} md={12}>
            <ReadOnly when={path.length === 0 && readOnly.includes(name)}>
              <FieldItem path={fieldPath} node={resolved} required={parent.required?.includes(name)} value={fieldValue}>
                {override ? (
                  override({ value: fieldValue, onChange: (next) => set(fieldPath, next) })
                ) : (
                  <FieldEditor path={fieldPath} node={resolved} root={schema} value={fieldValue} onChange={(next) => set(fieldPath, next)} />
                )}
              </FieldItem>
            </ReadOnly>
          </Col>
        )
      })}
    </Row>
  )

  return (
    <Form layout="vertical" disabled={disabled} requiredMark="optional" component="div">
      <Collapse
        defaultActiveKey={['fixSessionId', 'general']}
        items={[
          ...sections.filter(([name]) => name === 'fixSessionId').map(([name, node]) => section(name, node)),
          { key: 'general', label: 'General', children: fields(general, [], schema) },
          ...sections.filter(([name]) => name !== 'fixSessionId').map(([name, node]) => section(name, node)),
        ]}
      />
    </Form>
  )

  function section(name: string, node: JsonSchema) {
    const resolved = resolve(node, schema)
    const override = overrides[name]
    const sectionValue = getAt(value, [name])
    const editor = override ? (
      override({ value: sectionValue, onChange: (next) => set([name], next) })
    ) : kindOf(resolved) === 'object' ? (
      fields(Object.entries(resolved.properties ?? {}), [name], resolved)
    ) : (
      <ObjectList path={[name]} node={resolved} root={schema} value={sectionValue} onChange={(next) => set([name], next)} />
    )
    return {
      key: name,
      label: (
        <Space size={4}>
          {humanize(name)}
          <Tooltip title={<Description path={[name]} text={resolved.description} />}>
            <QuestionCircleOutlined />
          </Tooltip>
        </Space>
      ),
      children: <ReadOnly when={readOnly.includes(name)}>{editor}</ReadOnly>,
    }
  }
}

function ReadOnly({ when, children }: { when: boolean; children: ReactNode }) {
  return when ? <Form component={false} disabled>{children}</Form> : <>{children}</>
}

function FieldItem({ path, node, required, value, children }: { path: string[]; node: JsonSchema; required?: boolean; value: JsonValue | undefined; children: ReactNode }) {
  const unset = value === undefined && node.default !== undefined
  return (
    <Form.Item
      label={humanize(path[path.length - 1]!)}
      tooltip={<Description path={path} text={node.description} />}
      required={required}
      extra={unset ? <>Default: <Tag>{display(node.default)}</Tag></> : undefined}
    >
      {children}
    </Form.Item>
  )
}

interface EditorProps {
  /**
   * The field's names from the document's root, `[]` standing for any item of a list.
   */
  path: string[]
  node: JsonSchema
  root: JsonSchema
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
}

function FieldEditor({ path, node, root, value, onChange }: EditorProps) {
  const placeholder = node.default !== undefined ? display(node.default) : undefined
  switch (kindOf(node)) {
    case 'boolean':
      return <Switch checked={(value ?? node.default ?? false) as boolean} onChange={onChange} />
    case 'enum':
      return (
        <Select
          allowClear
          value={value as string | undefined}
          placeholder={placeholder}
          onChange={(next) => onChange(next ?? undefined)}
          options={node.enum!.map((option) => ({ value: option, label: option }))}
        />
      )
    case 'integer':
    case 'number':
      return (
        <InputNumber
          style={{ width: '100%' }}
          precision={kindOf(node) === 'integer' ? 0 : undefined}
          value={value as number | undefined}
          placeholder={placeholder}
          onChange={(next) => onChange(next ?? undefined)}
        />
      )
    case 'duration':
      return <DurationEditor value={value as string | undefined} placeholder={placeholder} onChange={onChange} />
    case 'stringList':
      return (
        <Select
          mode="tags"
          open={false}
          value={(value as string[] | undefined) ?? []}
          placeholder="Type a value and press enter"
          onChange={(next: string[]) => onChange(next.length > 0 ? next : undefined)}
        />
      )
    case 'map':
      return <MapEditor value={value as JsonObject | undefined} onChange={onChange} />
    case 'object':
      return <NestedObject path={path} node={node} root={root} value={value} onChange={onChange} />
    case 'objectList':
      return <ObjectList path={path} node={node} root={root} value={value} onChange={onChange} />
    default:
      return (
        <Input
          allowClear
          value={value as string | undefined}
          placeholder={placeholder}
          status={typeof value === 'string' && node.pattern && !new RegExp(node.pattern).test(value) ? 'error' : undefined}
          onChange={(event) => onChange(event.target.value === '' ? undefined : event.target.value)}
        />
      )
  }
}

function DurationEditor({ value, placeholder, onChange }: { value?: string; placeholder?: string; onChange: (value: string | undefined) => void }) {
  const parsed = value ? parseDuration(value) : undefined
  const unit: DurationUnit = parsed?.unit ?? (placeholder ? parseDuration(placeholder)?.unit : undefined) ?? 's'
  return (
    <Space.Compact style={{ width: '100%' }}>
      <InputNumber
        style={{ width: '100%' }}
        min={0}
        value={parsed?.amount}
        placeholder={placeholder ? String(parseDuration(placeholder)?.amount ?? placeholder) : undefined}
        onChange={(amount) => onChange(amount === null || amount === undefined ? undefined : formatDuration(amount, unit))}
      />
      <Select
        style={{ width: 140 }}
        value={unit}
        onChange={(next: DurationUnit) => parsed && onChange(formatDuration(parsed.amount, next))}
        options={DURATION_UNITS.map((u) => ({ value: u.value, label: u.label }))}
      />
    </Space.Compact>
  )
}

export function MapEditor({ value, onChange }: { value?: JsonObject; onChange: (value: JsonObject | undefined) => void }) {
  const entries = Object.entries(value ?? {})
  const update = (next: [string, JsonValue][]) => onChange(next.length > 0 ? Object.fromEntries(next) : undefined)
  return (
    <Flex vertical gap={8}>
      {entries.map(([key, entryValue], i) => (
        <Space.Compact key={i} style={{ width: '100%' }}>
          <Input
            placeholder="Name"
            value={key}
            onChange={(event) => update(entries.map((entry, j) => (j === i ? [event.target.value, entry[1]] : entry)))}
          />
          <Input
            placeholder="Value"
            value={String(entryValue ?? '')}
            onChange={(event) => update(entries.map((entry, j) => (j === i ? [entry[0], event.target.value] : entry)))}
          />
          <Button icon={<DeleteOutlined />} aria-label="Remove" onClick={() => update(entries.filter((_, j) => j !== i))} />
        </Space.Compact>
      ))}
      <Button icon={<PlusOutlined />} onClick={() => update([...entries, ['', '']])} style={{ alignSelf: 'flex-start' }}>
        Add
      </Button>
    </Flex>
  )
}

function NestedObject({ path, node, root, value, onChange }: EditorProps) {
  const object = (value && typeof value === 'object' && !Array.isArray(value) ? value : {}) as JsonObject
  return (
    <Row gutter={16}>
      {Object.entries(node.properties ?? {}).map(([name, child]) => {
        const resolved = resolve(child, root)
        return (
          <Col key={name} xs={24} md={12}>
            <FieldItem path={[...path, name]} node={resolved} required={node.required?.includes(name)} value={object[name]}>
              <FieldEditor
                path={[...path, name]}
                node={resolved}
                root={root}
                value={object[name]}
                onChange={(next) => onChange(setAt(object, [name], next))}
              />
            </FieldItem>
          </Col>
        )
      })}
    </Row>
  )
}

function ObjectList({ path, node, root, value, onChange }: EditorProps) {
  const items = Array.isArray(value) ? value : []
  const update = (next: JsonValue[]) => onChange(next.length > 0 ? next : undefined)
  return (
    <Flex vertical gap={12}>
      {items.map((item, i) => (
        <Card
          key={i}
          size="small"
          extra={<Button type="text" icon={<DeleteOutlined />} aria-label="Remove" onClick={() => update(items.filter((_, j) => j !== i))} />}
        >
          <NestedObject path={[...path, '[]']} node={resolve(node.items!, root)} root={root} value={item} onChange={(next) => update(items.map((it, j) => (j === i ? next! : it)))} />
        </Card>
      ))}
      <Button icon={<PlusOutlined />} onClick={() => update([...items, {}])} style={{ alignSelf: 'flex-start' }}>
        Add
      </Button>
    </Flex>
  )
}

/**
 * The field's name as files, the API and the audit trail write it, then what it does.
 */
function Description({ path, text }: { path: string[]; text?: string }) {
  return (
    <Flex vertical gap={4}>
      <code>{path.join('.').replaceAll('.[]', '[]')}</code>
      {text && <span style={{ whiteSpace: 'pre-line' }}>{text}</span>}
    </Flex>
  )
}

function display(value: unknown): string {
  if (typeof value === 'string') {
    const duration = parseDuration(value)
    return duration ? `${duration.amount} ${DURATION_UNITS.find((u) => u.value === duration.unit)!.label}` : value
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
