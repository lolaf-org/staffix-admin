/**
 * The subset of JSON Schema an engine's session settings schema uses.
 */
export interface JsonSchema {
  $ref?: string
  $defs?: Record<string, JsonSchema>
  type?: string
  format?: string
  pattern?: string
  minLength?: number
  enum?: string[]
  description?: string
  default?: unknown
  properties?: Record<string, JsonSchema>
  required?: string[]
  items?: JsonSchema
  additionalProperties?: JsonSchema | boolean
}

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue }
export type JsonObject = { [key: string]: JsonValue }

export type FieldKind = 'object' | 'map' | 'objectList' | 'stringList' | 'enum' | 'boolean' | 'integer' | 'number' | 'duration' | 'string'

export function resolve(node: JsonSchema, root: JsonSchema): JsonSchema {
  if (!node.$ref) return node
  const name = node.$ref.substring(node.$ref.lastIndexOf('/') + 1)
  return root.$defs?.[name] ?? {}
}

export function kindOf(node: JsonSchema): FieldKind {
  if (node.enum) return 'enum'
  switch (node.type) {
    case 'object':
      return node.properties ? 'object' : 'map'
    case 'array':
      return node.items?.properties ? 'objectList' : 'stringList'
    case 'boolean':
      return 'boolean'
    case 'integer':
      return 'integer'
    case 'number':
      return 'number'
    default:
      return node.format === 'duration' ? 'duration' : 'string'
  }
}

/**
 * Names a rule cannot phrase: FIX field names operators know as written, and words the rule would split.
 */
const LABELS: Record<string, string> = {
  fixTSession: 'FIXT session',
  heartBeatInterval: 'Heartbeat interval',
  senderCompID: 'SenderCompID',
  senderSubID: 'SenderSubID',
  senderLocationID: 'SenderLocationID',
  targetCompID: 'TargetCompID',
  targetSubID: 'TargetSubID',
  targetLocationID: 'TargetLocationID',
  advertiseMsgTypeGrpOnLogon: 'Advertise MsgTypeGrp on logon',
  enabledLogonNextExpectedMsgSeqNum: 'NextExpectedMsgSeqNum on logon',
  resetSeqNumOnLogon: 'Reset sequence numbers on logon',
  probeTestReqIdPrefix: 'Probe TestReqID prefix',
  expectedDeliverToCompIds: 'Expected DeliverToCompIDs',
  expectedOnBehalfOfCompIds: 'Expected OnBehalfOfCompIDs',
  validateCompId: 'Validate CompIDs',
  validateBeginString: 'Validate BeginString',
  nonStopSchedules: 'Non-stop schedules',
}

const WORDS: Record<string, string> = { id: 'ID', ids: 'IDs', fix: 'FIX', rtt: 'RTT', cod: 'COD', ema: 'EMA' }

/**
 * `logInOrOutResponseTimeout` reads as "Log in or out response timeout"; abbreviations stay capitals.
 */
export function humanize(name: string): string {
  if (LABELS[name]) return LABELS[name]
  const words = name.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2').toLowerCase().split(' ')
  const label = words.map((word) => WORDS[word] ?? word).join(' ')
  return label.charAt(0).toUpperCase() + label.slice(1)
}

export function getAt(value: JsonValue | undefined, path: string[]): JsonValue | undefined {
  return path.reduce<JsonValue | undefined>(
    (current, key) => (current && typeof current === 'object' && !Array.isArray(current) ? current[key] : undefined),
    value,
  )
}

/**
 * A copy of `value` with `path` set; undefined removes it, so the engine applies its default.
 */
export function setAt(value: JsonObject, path: string[], next: JsonValue | undefined): JsonObject {
  const [key, ...rest] = path
  const copy: JsonObject = { ...value }
  if (rest.length === 0) {
    if (next === undefined) delete copy[key!]
    else copy[key!] = next
    return copy
  }
  const child = value[key!]
  copy[key!] = setAt(child && typeof child === 'object' && !Array.isArray(child) ? child : {}, rest, next)
  return copy
}

/**
 * Each problem as `path: message`: the required fields left empty and the values the schema's patterns refuse.
 * The engine checks the rest.
 */
export function problems(root: JsonSchema, value: JsonObject): string[] {
  const found: string[] = []
  const walk = (node: JsonSchema, current: JsonValue | undefined, path: string) => {
    const schema = resolve(node, root)
    if (kindOf(schema) === 'object') {
      const object = current && typeof current === 'object' && !Array.isArray(current) ? current : undefined
      Object.entries(schema.properties ?? {}).forEach(([name, child]) => {
        const childValue = object?.[name]
        const childPath = path ? `${path}.${name}` : name
        if (schema.required?.includes(name) && (childValue === undefined || childValue === '')) {
          if (object || !path) found.push(`${childPath}: required`)
        } else {
          walk(child, childValue, childPath)
        }
      })
    } else if (kindOf(schema) === 'objectList' && Array.isArray(current)) {
      current.forEach((item, i) => walk(schema.items!, item, `${path}[${i}]`))
    } else if (typeof current === 'string' && schema.pattern && !new RegExp(schema.pattern).test(current)) {
      found.push(`${path}: not a valid value`)
    }
  }
  walk(root, value, '')
  return found
}

/**
 * How the engine shows a secret's value; sent back unchanged, the engine keeps the value it has.
 */
export const SECRET_MASK = '******'
