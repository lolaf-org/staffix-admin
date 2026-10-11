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
import { useAlerts, useEngines } from '../api/queries'

/**
 * The engines whose alerts the console cannot tell: not announcing themselves, or not answering for their alerts.
 * An engine card also counts its own failing sessions poll, which this cannot see.
 */
export function useEnginesWithUnknownAlerts(): Set<string> {
  const engines = useEngines()
  const alerts = useAlerts()
  return new Set([
    ...(engines.data ?? []).filter((engine) => !engine.reachable).map((engine) => engine.engineId),
    ...(alerts.data?.unavailableEngineIds ?? []),
  ])
}
