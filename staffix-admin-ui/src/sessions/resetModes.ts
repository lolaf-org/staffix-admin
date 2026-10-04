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
