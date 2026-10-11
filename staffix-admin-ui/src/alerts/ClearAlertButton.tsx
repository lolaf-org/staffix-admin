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
import { useClearAlert, type SessionRef } from '../api/queries'
import { WriteButton } from '../sessions/WriteButton'

/**
 * Offered on every alert: whether the engine accepts depends on the probe's evaluator, so a refusal is reported
 * rather than the button hidden.
 */
export function ClearAlertButton({ sessionRef, probe }: { sessionRef: SessionRef, probe: string }) {
  const clear = useClearAlert(sessionRef)
  return (
    <span onClick={(event) => event.stopPropagation()}>
      <WriteButton
        size="small"
        loading={clear.isPending}
        confirm={`Clear the ${probe} alert? Only an alert raised from application code accepts it.`}
        onConfirm={() => clear.mutate(probe)}
      >
        Clear
      </WriteButton>
    </span>
  )
}
