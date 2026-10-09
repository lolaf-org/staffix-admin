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
