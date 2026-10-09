# Sessions

## Overview

The Sessions page shows every announced engine, its groups and one box per FIX session, coloured by the status the
engine reports:

| Colour | Status |
|---|---|
| Green | Logged in |
| Yellow | Logged out, outside session time |
| Orange | Logged out by an operator (inside or outside session time) |
| Red | Logged out within session time: the one worth an alert |

The counts per status filter the page, as do a search on group, session, instance or comp id, and an engine picker;
the URL keeps the filters, to share them. A session box opens the session's panel; an engine silent for longer than
`staffix.admin.engine-heartbeat-timeout` shows unreachable.

## Status tab

The session's identity, desired state and sequence numbers, and its controls (writers only):

- **Log on** and **Log out**. A session logged out by an operator stays out until logged on again.
- **Set seq nums**: the next incoming and outgoing sequence numbers.
- **Sequence reset**: in session (Logon with ResetSeqNumFlag=Y over the live connection), logout then logon with reset
  (initiators only), or local only, which breaks the session unless the peer does the same.
- **Configs** of an initiator with backups: switch to another target; the selected one is logged out first.
- **Remove session**, for an acceptor session kept in a session settings store.

### Availability

The session's status over a period as a band, from the `session.logon.status` gauge its monitoring plugin exports:
logged in, logged out within session time, by an operator, outside session time, or no data. The availability is the
time logged in over the time it was meant to be (logged in or logged out within session time): an operator logout and
missing data do not count against it. Session time is as the engine evaluated it then, so a schedule changed since
does not alter the past.

Each step of the band is a whole number of the engine's exports, and its shares count every sample in it, so an
outage shorter than the step still shows. A session without a monitoring plugin has no availability. An engine
that shuts down reports its sessions logged out by an operator. Some backends keep returning a stopped or crashed
engine's last status for a while before it shows as no data: see [Backends](backends.md).

## Settings tab

The session's settings as the engine stores them, in a form built from the engine's own session settings schema:
each field's tooltip gives its name as files and the API write it and what it does, and an unset field shows the
default the engine applies. Writers edit and save them; the engine restarts the session on them unless its "Restart
live session on update" is off.

- The factory, application, store, logger and plugins are picked from what the engine has; a value it does not know
  is shown and flagged, and the engine refuses it at save.
- Application settings the session's application declares are listed with their descriptions; a secret one is typed
  blind and left empty keeps its value. Certificates stay as the engine's files configure them.
- A field a session file sets through a `${...}` placeholder keeps it when its value is not changed; changing it is
  refused, as its source owns it.
- A store backed by files keeps the change; one held in memory loses it when the engine restarts, which the tab says.
- Every change is audited by the names of the fields it changed, never their values.

### Adding and removing sessions

Writers add acceptor sessions from an engine's card ("Acceptor session"), blank or starting from another acceptor
session's settings, into one of the engine's session settings stores, and remove them from their Status tab. An
initiator's sessions are the targets it was built with: the console neither adds nor removes them, nor changes their
identity.

## Send message tab

Writers send application messages through the session, one per line (`35=D|11=ORDER-1|55=AAPL|...`), in order; the
first message the engine refuses stops the rest, and the error names it. The field separator is `|` or another
character of your choice; PossDup sends them as possible duplicates. **Decode** shows the message under the cursor
with the session's dictionary before sending it.

## Metrics and Logs tabs

The session's own [metrics](metrics.md) and [logs](logs-and-trace.md), read from the backends of its emitters.
