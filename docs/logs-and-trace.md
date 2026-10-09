# Logs and trace

FIX logs come from the backend of each session's messages logger (see [Connectors](connectors.md)), never from the
engine: every message in and out, and session events, as staffix's OTLP messages logger sends them.

## Logs

The Logs page searches an engine, one of its groups or one session; a session's Logs tab searches that session. Lines
are filtered by text, message type and direction (in, out, event), over a [period](metrics.md#period-and-rate-window)
of up to 7 days, live, or a custom range, newest first and up to a line limit (50 to 500).

- **Decode**: a message opens decoded with its session's own dictionaries, field by field with repeating groups, as
  raw, or with the session's [metrics around it](metrics.md#around-a-logged-message). Its ids link to their trace,
  and **Open session** opens the panel of the session that handled it.
- **CSV**: exports the lines found, every page: time as the backend keeps it (down to the nanosecond), engine, group,
  session, type and message.

## Order trace

The Trace page follows an id (ClOrdID, OrderID, QuoteReqID...) across the sessions of every engine, or of one: every
logged message with a field equal to it, whatever the tag, oldest first. From the messages found it follows the linked
ids (ClOrdID, OrigClOrdID, OrderID, QuoteReqID, QuoteID), so a trace from a ClOrdID also finds the executions carrying
only the OrderID and the replaces. It stops after 3 rounds of linked ids, 20 ids, or 1000 messages per engine and
round, and says so. The URL holds the trace, to share it.

The match is exact: `ORD-1` does not find `ORD-12`. A message logged with SOH between fields matches like one logged
with `|`.

### Trace settings

The linking tags and the fields shown for each message are those above unless `console.yaml` sets them; a list left
out keeps the defaults, an empty one means none. They are edited in the file only, and apply without a restart:

```yaml
trace:
  linkingTags: [11, 41, 37, 526]       # 526 SecondaryClOrdID
  keyFields:
    - tag: 11
      name: ClOrdID
    - tag: 526
      name: SecondaryClOrdID
```

## What a search costs

The engines are never involved: the backend runs the search and returns the matching lines only, at most the line
limit. A backend that indexes the message content finds a value whatever the period; one that does not reads the
selected sessions' lines until it has the limit's worth of matches, so a rare value over a long period costs it the
whole period. See [Backends](backends.md).
