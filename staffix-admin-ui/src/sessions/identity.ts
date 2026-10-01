import type { Schemas } from '../api/client'

type CompIds = Schemas['CompIds']

/**
 * `COMP/sub/location`, the sub and location ids only when set.
 */
export function formatCompIds(ids: CompIds): string {
  return [ids.compId, ids.subId, ids.locationId].filter(Boolean).join('/')
}

export function formatIdentity(identity: Schemas['FixIdentity']): string {
  const version = identity.defaultApplVerId ? `${identity.fixVersion} (${identity.defaultApplVerId})` : identity.fixVersion
  return `${version} ${formatCompIds(identity.sender)} → ${formatCompIds(identity.target)}`
}
