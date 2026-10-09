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

type ResetMode = Schemas['ResetRequest']['mode']

/**
 * From staffix's ResetFixSessionMode documentation.
 */
export const RESET_MODES: { mode: ResetMode; label: string; description: string; initiatorOnly?: boolean }[] = [
  {
    mode: 'RESET_SEQUENCE_IN_SESSION',
    label: 'Reset in session',
    description: 'Logon with ResetSeqNumFlag=Y over the live connection. The peer is told; the connection is kept.',
  },
  {
    mode: 'LOGOUT_LOGON_RESET_NUM_FLAG',
    label: 'Logout, then logon with reset',
    description: 'Drops the connection and logs back on with ResetSeqNumFlag=Y. The peer is told. Initiators only.',
    initiatorOnly: true,
  },
  {
    mode: 'RESET_SEQUENCE',
    label: 'Local reset only',
    description: 'Puts both numbers back to 1 without telling the peer. Breaks the session unless the peer does the same.',
  },
]
