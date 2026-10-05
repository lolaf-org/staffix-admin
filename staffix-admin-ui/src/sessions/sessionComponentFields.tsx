import { Button, Flex, Select, Typography } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import type { SessionComponents } from '../api/queries'
import type { FieldOverride } from '../settings/SchemaForm'
import type { JsonObject, JsonValue } from '../settings/schema'

/**
 * staffix's own: what an unset instance id means.
 */
const DEFAULT_ID = 'default'

/**
 * The settings fields naming one of the engine's components, as staffix's FixSessionSettings names them; a server
 * test pins these names against staffix.
 */
export const COMPONENT_FIELDS = {
  applicationFactory: 'fixApplicationFactoryInstanceId',
  application: 'fixApplicationInstanceId',
  messagesStore: 'fixMessageStoreInstanceId',
  messagesLogger: 'fixMessageLoggerInstanceId',
  sessionsPlugins: 'fixSessionPluginsInstanceIds',
} as const

/**
 * Drop-downs of what the engine has for the fields naming its components; none when the engine does not say, which
 * leaves them free text. The application's choices follow the factory picked in `document`.
 */
export function sessionComponentOverrides(components: SessionComponents | undefined, document: JsonObject): Record<string, FieldOverride> {
  if (!components) return {}
  const factoryId = (document[COMPONENT_FIELDS.applicationFactory] as string | undefined) ?? DEFAULT_ID
  const factory = components.applicationFactories.find((f) => f.instanceId === factoryId)
  return {
    [COMPONENT_FIELDS.applicationFactory]: (props) => (
      <ComponentSelect {...props} choices={components.applicationFactories.map((f) => f.instanceId)} what="application factory" />
    ),
    [COMPONENT_FIELDS.application]: (props) => (
      <ComponentSelect {...props} choices={factory?.applicationIds ?? []} what={`application of factory ${factoryId}`} />
    ),
    [COMPONENT_FIELDS.messagesStore]: (props) => <ComponentSelect {...props} choices={components.messagesStores} what="message store" />,
    [COMPONENT_FIELDS.messagesLogger]: (props) => (
      <ComponentSelect {...props} choices={components.messagesLoggers} what="messages logger" anyWhenNone />
    ),
    [COMPONENT_FIELDS.sessionsPlugins]: (props) => <PluginsEditor {...props} plugins={components.sessionsPlugins} />,
  }
}

interface ComponentSelectProps {
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
  choices: string[]
  what: string
  /**
   * The engine has none of these and accepts any id, as it does for messages loggers.
   */
  anyWhenNone?: boolean
}

function ComponentSelect({ value, onChange, choices, what, anyWhenNone }: ComponentSelectProps) {
  const id = typeof value === 'string' ? value : undefined
  const effective = id ?? DEFAULT_ID
  const missing = !choices.includes(effective) && !(anyWhenNone && choices.length === 0)
  return (
    <Flex vertical gap={4}>
      <Select
        allowClear
        showSearch
        value={id}
        placeholder={DEFAULT_ID}
        status={missing ? 'warning' : undefined}
        onChange={(next) => onChange(next ?? undefined)}
        options={[...choices, ...(id !== undefined && !choices.includes(id) ? [id] : [])].map((choice) => ({
          value: choice,
          label: choices.includes(choice) ? choice : `${choice} (not set up in this engine)`,
        }))}
      />
      {missing && (
        <Typography.Text type="warning" style={{ fontSize: 12 }}>
          This engine has no {what} '{effective}': the engine refuses these settings.
        </Typography.Text>
      )}
    </Flex>
  )
}

interface PluginsEditorProps {
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
  plugins: SessionComponents['sessionsPlugins']
}

function PluginsEditor({ value, onChange, plugins }: PluginsEditorProps) {
  const selected: JsonObject = value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const types = [...new Set(plugins.flatMap((plugin) => plugin.pluginTypes))].sort()
  const update = (next: JsonObject) => onChange(Object.keys(next).length > 0 ? next : undefined)
  const unused = types.filter((type) => !(type in selected))
  return (
    <Flex vertical gap={8}>
      {Object.entries(selected).map(([type, instanceId]) => {
        const serving = plugins.filter((plugin) => plugin.pluginTypes.includes(type)).map((plugin) => plugin.instanceId)
        const known = typeof instanceId === 'string' && serving.includes(instanceId)
        return (
          <Flex key={type} gap={8} align="center">
            <Typography.Text style={{ flex: '1 1 0', minWidth: 0 }} ellipsis={{ tooltip: type }}>
              {simpleName(type)}
            </Typography.Text>
            <Select
              style={{ flex: '1 1 0', minWidth: 0 }}
              value={instanceId as string}
              status={known ? undefined : 'warning'}
              onChange={(next) => update({ ...selected, [type]: next })}
              options={[...serving, ...(known ? [] : [instanceId as string])].map((id) => ({
                value: id,
                label: serving.includes(id) ? id : `${id} (not set up in this engine)`,
              }))}
              aria-label={`Instance of ${simpleName(type)}`}
            />
            <Button
              type="text"
              icon={<DeleteOutlined />}
              aria-label={`Remove ${simpleName(type)}`}
              onClick={() => {
                const next = { ...selected }
                delete next[type]
                update(next)
              }}
            />
          </Flex>
        )
      })}
      {unused.length > 0 ? (
        <Select
          value={null}
          placeholder={<><PlusOutlined /> Add a plugin type</>}
          onChange={(type: string) =>
            update({ ...selected, [type]: plugins.find((plugin) => plugin.pluginTypes.includes(type))!.instanceId })
          }
          options={unused.map((type) => ({ value: type, label: simpleName(type), title: type }))}
          aria-label="Add a plugin type"
        />
      ) : (
        Object.keys(selected).length === 0 && <Typography.Text type="secondary">This engine has no session plugins.</Typography.Text>
      )}
    </Flex>
  )
}

function simpleName(className: string): string {
  return className.substring(className.lastIndexOf('.') + 1)
}
